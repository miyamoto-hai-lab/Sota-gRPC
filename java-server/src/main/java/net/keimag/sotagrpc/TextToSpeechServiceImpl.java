package net.keimag.sotagrpc;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import jp.vstone.RobotLib.CPlayWave;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.sotatalk.TextToSpeechSota;
import net.keimag.sotagrpc.v1.robotlib.PlayAudioResponse;
import net.keimag.sotagrpc.v1.sotatalk.SpeechConfig;
import net.keimag.sotagrpc.v1.sotatalk.TTSDataRequest;
import net.keimag.sotagrpc.v1.sotatalk.GetTTSDataResponse;
import net.keimag.sotagrpc.v1.sotatalk.TextToSpeechServiceGrpc;
import sotagrpc.v1.Common;

import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;

public class TextToSpeechServiceImpl extends TextToSpeechServiceGrpc.TextToSpeechServiceImplBase {
    private static final String TAG = "Sota-gRPC.MotionAsSotaWishService";
    private final BlockingQueue<Main.SotaTask<?>> commandQueue;

    public TextToSpeechServiceImpl(BlockingQueue<Main.SotaTask<?>> commandQueue) {
        this.commandQueue = commandQueue;
    }

    /**
     * <pre>
     * テキストを音声データに変換する
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void getTTSData(TTSDataRequest request, StreamObserver<GetTTSDataResponse> responseObserver) {
        CRobotUtil.Log(TAG,"RPC call: getTTSData");
        try {
            String text = request.getText();
            byte[] data;
            if (request.hasConfig()) {
                SpeechConfig speechConfig = request.getConfig();
                int speechRate = speechConfig.getSpeechRate();
                int intonation = speechConfig.getIntonation();
                int pitch = speechConfig.getPitch();
                //        if (speechConfig.hasLanguageCode()) {
                //            callSetLocalizeReflectively(speechConfig.getLanguageCode());
                //        }
                data = TextToSpeechSota.getTTSData(text, speechRate, pitch, intonation);
            } else {
                data = TextToSpeechSota.getTTSData(text);
            }
            responseObserver.onNext(GetTTSDataResponse.newBuilder().setAudioData(ByteString.copyFrom(data)).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

//    /**
//     * @param request
//     * @param responseObserver
//     */
//    @Override
//    public void say(TTSDataRequest request, StreamObserver<Common.SuccessResponse> responseObserver) {
//        CRobotUtil.Log(TAG,"RPC call: say");
//        try {
//            String text = request.getText();
//            byte[] data;
//            if (request.hasConfig()) {
//                SpeechConfig speechConfig = request.getConfig();
//                int speechRate = speechConfig.getSpeechRate();
//                int intonation = speechConfig.getIntonation();
//                int pitch = speechConfig.getPitch();
//                //        if (speechConfig.hasLanguageCode()) {
//                //            callSetLocalizeReflectively(speechConfig.getLanguageCode());
//                //        }
//                data = TextToSpeechSota.getTTSData(text, speechRate, pitch, intonation);
//            } else {
//                data = TextToSpeechSota.getTTSData(text);
//            }
//            CompletableFuture<PlayAudioResponse> future = new CompletableFuture<>();
//            commandQueue.put(new Main.SotaTask<>());
//            responseObserver.onNext(GetTTSDataResponse.newBuilder().setAudioData(ByteString.copyFrom(data)).build());
//            responseObserver.onCompleted();
//        } catch (Exception e) {
//            e.printStackTrace();
//            responseObserver.onError(e);
//        }
//    }

    /**
     * リフレクションを使い、TextToSpeechSota.setLocalize(String)を安全に呼び出す。
     * メソッドが存在しない場合は、メッセージを出力して何もしない。
     *
     * @param languageCode 設定する言語コード
     */
    private void callSetLocalizeReflectively(String languageCode) {
        try {
            // ★★★ ご自身の環境に合わせてTextToSpeechSotaの完全修飾クラス名に変更してください ★★★
            String className = "jp.vstone.sotatalk.TextToSpeechSota";

            // 1. クラスの情報を取得
            Class<?> clazz = Class.forName(className);

            // 2. 呼び出したいメソッドの情報を取得 (メソッド名と引数の型を指定)
            Method method = clazz.getMethod("setLocalize", String.class);

            // 3. メソッドを実行 (静的メソッドなので第一引数はnull, 第二引数以降が実引数)
            method.invoke(null, languageCode);

            System.out.println("リフレクション経由で setLocalize() を呼び出しました。");

        } catch (NoSuchMethodException e) {
            // メソッドが見つからなかった場合 (古いjarの可能性)
            System.out.println("setLocalize() メソッドが見つかりません。バージョン互換のため処理をスキップします。");
        } catch (ClassNotFoundException e) {
            // クラス自体が見つからなかった場合
            System.err.println("エラー: " + e.getMessage() + " クラスが見つかりません。");
        } catch (Exception e) {
            // その他の例外 (呼び出し権限がない、など)
            System.err.println("リフレクション実行中に予期せぬエラーが発生しました。");
            e.printStackTrace();
        }
    }
}
