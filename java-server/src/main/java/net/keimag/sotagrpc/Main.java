package net.keimag.sotagrpc;

import io.grpc.ServerBuilder;

// Sotaのライブラリをインポート
import jp.vstone.RobotLib.CRecordMic;
import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.sotatalk.MotionAsSotaWish;
import jp.vstone.sotatalk.SpeechRecog;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

public class Main {
    /**
     * スレッドセーフでないSotaLibクラスのインスタンスをまとめて管理するクラス
     */
    public static class SotaContext {
        public final CRobotMem mem;
        public final CSotaMotion motion;
        public final SpeechRecog speechRecog;
//        public final CPlayWave player; // 例：音声再生機能
        public final MotionAsSotaWish motionAsSotaWish;
        // 他のライブラリもここに追加していく (CRecordMic, SpeechRecog など)

        public SotaContext() {
            String TAG = "SotaContext";
            CRobotUtil.Log(TAG, "Initializing Sota connection...");
            this.mem = new CRobotMem();
            if (!mem.Connect()) {
                throw new RuntimeException("Failed to connect to Sota.");
            } else {
                CRobotUtil.Log(TAG, "VSMD connection established.");
            }

            // Sotaの各種スレッドアンセーフライブラリを初期化
            this.motion = new CSotaMotion(mem);
            this.speechRecog = new SpeechRecog(motion);
            this.motionAsSotaWish = new MotionAsSotaWish(motion);
            this.motion.InitRobot_Sota();
        }
    }

    /**
     * SotaLib呼び出しをSota Threadで行う際のインターフェース
     */
    public static class SotaTask<T> {
        private final Function<SotaContext, T> function;
        private final CompletableFuture<T> future;

        public SotaTask(Function<SotaContext, T> procedure, CompletableFuture<T> future) {
            this.function = procedure;
            this.future = future;
        }

        public void execute(SotaContext context) {
            try {
                T result = function.apply(context);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }

        @Override
        public String toString() {
            return this.function.toString();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String TAG = "Sota-gRPC";
        int port = 8080;
        System.out.println("Sota-gRPC server version 1.0.0");
        // Sotaの各種ライブラリを初期
        CRecordMic recordMic = new CRecordMic();
        // TextToSpeechSotaは静的メソッドのみなのでインスタンス化は不要
//        CRobotUtil.Log("Sota", "Firmware Rev. " + sotaContext.mem.FirmwareRev.get());

        // 1. コマンドをやり取りするためのキューを作成
        BlockingQueue<SotaTask<?>> commandQueue = new LinkedBlockingQueue<>();

        // 2. Sotaのスレッドアンセーフライブラリを操作する専用スレッドを起動
        Thread sotaThread = new Thread(() -> {
            // ★★★★★ 専用スレッドの内部で、Sotaの初期化を行う ★★★★★
            SotaContext sotaContext;
            sotaContext = new SotaContext();
            CRobotUtil.Log(TAG, "SotaContext created successfully inside sotaThread.");

            // 初期化後、コマンドの処理ループを開始
            try {
                while (true) {
                    System.out.println("Waiting task for Sota Thread...");
                    SotaTask<?> task = commandQueue.take();
                    // 初期化済みのsotaContextを使ってタスクを実行
                    task.execute(sotaContext);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CRobotUtil.Err("Sota Thread", "SotaThread was interrupted.");
            }
        });
        sotaThread.setContextClassLoader(Thread.currentThread().getContextClassLoader()); // 専用スレッドにmainスレッドのクラスローダーをセットする
        sotaThread.start(); // スレッド開始
        CRobotUtil.Log(TAG, "Sota thread started.");

        // gRPCサーバーを起動
        CRobotUtil.Log(TAG, "Starting gRPC server...");
        io.grpc.Server server = ServerBuilder.forPort(port)
                .maxInboundMessageSize(512 * 1024 * 1024) // 例: 上限を512MBに設定
                .addService(new MotionServiceImpl(commandQueue))
                .addService(new MotionAsSotaWishServiceImpl(commandQueue))
                .addService(new PlaybackServiceImpl(commandQueue))
                .addService(new RecordingServiceImpl(recordMic))
//                .addService(new SpeechRecognitionServiceImpl(speechRecog))
                .addService(new TextToSpeechServiceImpl(commandQueue))
                .build();
        server.start();
        CRobotUtil.Log(TAG, "Server started on port " + port);

        server.awaitTermination();
    }
}