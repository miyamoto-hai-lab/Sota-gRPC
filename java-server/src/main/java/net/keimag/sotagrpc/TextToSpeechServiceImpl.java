package net.keimag.sotagrpc;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import jp.vstone.sotatalk.TextToSpeechSota;
import net.keimag.sotagrpc.v1.sotatalk.SpeechConfig;
import net.keimag.sotagrpc.v1.sotatalk.SynthesizeRequest;
import net.keimag.sotagrpc.v1.sotatalk.SynthesizeResponse;
import net.keimag.sotagrpc.v1.sotatalk.TextToSpeechServiceGrpc;

import java.lang.reflect.Method;

public class TextToSpeechServiceImpl extends TextToSpeechServiceGrpc.TextToSpeechServiceImplBase {
    /**
     * <pre>
     * テキストを音声データに変換する
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void synthesize(SynthesizeRequest request, StreamObserver<SynthesizeResponse> responseObserver) {
        String text = request.getText();
        SpeechConfig speechConfig = request.getConfig();
        int speechRate = speechConfig.getSpeechRate();
        int intontion = speechConfig.getIntonation();
        int pitch = speechConfig.getPitch();
//        if (speechConfig.hasLanguageCode()) {
//            callSetLocalizeReflectively(speechConfig.getLanguageCode());
//        }
        byte[] data = TextToSpeechSota.getTTSData(text,  speechRate, pitch, intontion);
        responseObserver.onNext(SynthesizeResponse.newBuilder().setAudioData(ByteString.copyFrom(data)).build());
        responseObserver.onCompleted();
    }

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
