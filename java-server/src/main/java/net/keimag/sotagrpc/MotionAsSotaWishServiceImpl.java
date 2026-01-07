package net.keimag.sotagrpc;

import io.grpc.stub.StreamObserver;
import jp.vstone.RobotLib.CRobotMotion;
import jp.vstone.RobotLib.CRobotUtil;
import net.keimag.sotagrpc.v1.robotlib.MotionServiceGrpc;
import net.keimag.sotagrpc.v1.robotlib.ServoOnResponse;
import net.keimag.sotagrpc.v1.sotatalk.*;
import jp.vstone.sotatalk.MotionAsSotaWish;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

public class MotionAsSotaWishServiceImpl extends MotionAsSotaWishServiceGrpc.MotionAsSotaWishServiceImplBase {
    private final BlockingQueue<Main.SotaTask<?>> commandQueue;

    private static final String TAG = "Sota-gRPC.MotionAsSotaWishService";

    public MotionAsSotaWishServiceImpl(BlockingQueue<Main.SotaTask<?>> commandQueue) {
        this.commandQueue = commandQueue;
    }

    /**
     * <pre>
     * 自然な身振りをしながら発話する (Say)
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void sayWithMotion(SayWithMotionRequest request, StreamObserver<SayWithMotionResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: sayWithMotion");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<Void>(
                    (sotaContext) -> {
                        String text = request.getText();
                        if (request.hasScene()) {
                            String scene = request.getScene().toString();
                            if (request.hasConfig()) {
                                SpeechConfig config = request.getConfig();
                                int pitch = config.getPitch();
                                int intonation = config.getIntonation();
                                int speechRate = config.getSpeechRate();
                                sotaContext.motionAsSotaWish.Say(text, scene, speechRate, pitch, intonation);
                            } else {
                                sotaContext.motionAsSotaWish.Say(text, scene);
                            }
                        } else {
                            sotaContext.motionAsSotaWish.Say(text);
                        }
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            SayWithMotionResponse response = SayWithMotionResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * <pre>
     * 事前に定義されたシーンのモーションを再生する (play)
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void playScene(PlaySceneRequest request, StreamObserver<PlaySceneResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: playScene");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<Void>(
                    (sotaContext) -> {
                        String scene = request.getScene().toString();
                        int duration = request.getTimeMs();
                        sotaContext.motionAsSotaWish.play(scene, duration);
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            PlaySceneResponse response = PlaySceneResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * <pre>
     * アイドリング（待機）モーションを開始・停止する
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void startIdling(StartIdlingRequest request, StreamObserver<StartIdlingResponse> responseObserver) {
        super.startIdling(request, responseObserver);
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void stopIdling(StopIdlingRequest request, StreamObserver<StopIdlingResponse> responseObserver) {
        super.stopIdling(request, responseObserver);
    }
}
