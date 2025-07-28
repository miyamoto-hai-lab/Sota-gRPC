package net.keimag.sotagrpc;

import com.google.common.collect.Lists;
import io.grpc.stub.StreamObserver;
import jp.vstone.sotatalk.SpeechRecog;
import net.keimag.sotagrpc.v1.sotatalk.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpeechRecognitionServiceImpl extends SpeechRecognitionServiceGrpc.SpeechRecognitionServiceImplBase {

    private final SpeechRecog speechRecog;

    public SpeechRecognitionServiceImpl(SpeechRecog speechRecog) {
        this.speechRecog = speechRecog;
    }

    /**
     * <pre>
     * 自由な内容で音声認識を実行する (getRecognition)
     * </pre>
     */
    @Override
    public void recognize(RecognizeRequest request, StreamObserver<RecognitionResult> responseObserver) {
        System.out.println("RPC call: recognize");
        try {
            // 言語設定を適用
//            if (request.hasLanguageCode()) {
//                callSetLangReflectively(this.speechRecog, request.getLanguageCode());
//            }

            // 音声認識を実行
            SpeechRecog.RecogResult nativeResult = this.speechRecog.getRecognition(request.getTimeoutMs());

            // gRPCのレスポンスメッセージに変換
            RecognitionResult grpcResult = toGrpcRecognitionResult(nativeResult);

            responseObserver.onNext(grpcResult);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * リフレクションを使い、SpeechRecog#setLang(String)を安全に呼び出す。
     * これはインスタンスメソッド用の実装です。
     *
     * @param instance     メソッドを呼び出す対象のインスタンス (this.speechRecog)
     * @param languageCode 設定する言語コード
     */
    private void callSetLangReflectively(SpeechRecog instance, String languageCode) {
        // インスタンスがnullの場合は何もしない
        if (instance == null) {
            System.err.println("エラー: SpeechRecogのインスタンスがnullです。");
            return;
        }

        try {
            // 1. インスタンスからクラスの情報を取得
            Class<?> clazz = instance.getClass();

            // 2. 呼び出したいメソッドの情報を取得
            Method method = clazz.getMethod("setLang", String.class);

            // 3. メソッドを実行 (インスタンスメソッドなので第一引数に対象インスタンスを指定)
            method.invoke(instance, languageCode);

            System.out.println("リフレクション経由で setLang() を呼び出しました。");

        } catch (NoSuchMethodException e) {
            // メソッドが見つからなかった場合
            System.out.println("setLang() メソッドが見つかりません。バージョン互換のため処理をスキップします。");
        } catch (Exception e) {
            // その他の例外
            System.err.println("リフレクション実行中に予期せぬエラーが発生しました。");
            e.printStackTrace();
        }
    }

    /**
     * <pre>
     * 「はい」または「いいえ」を認識する (getYesorNo)
     * </pre>
     */
    @Override
    public void recognizeYesOrNo(RecognizeYesOrNoRequest request, StreamObserver<RecognizeYesOrNoResponse> responseObserver) {
        System.out.println("RPC call: recognizeYesOrNo");
        try {
            String result = this.speechRecog.getYesorNo(request.getTimeoutMs(), request.getRetryCount());

            RecognizeYesOrNoResponse.Builder responseBuilder = RecognizeYesOrNoResponse.newBuilder();

            if (result != null) {
                if (result.equals(SpeechRecog.ANSWER_YES)) {
                    responseBuilder.setAnswer(YesNoAnswer.YES);
                } else if (result.equals(SpeechRecog.ANSWER_NO)) {
                    responseBuilder.setAnswer(YesNoAnswer.NO);
                }
            }
            // null or other cases default to UNSPECIFIED

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * <pre>
     * 人の名前を認識する (getName)
     * </pre>
     */
    @Override
    public void recognizeName(RecognizeNameRequest request, StreamObserver<RecognizeNameResponse> responseObserver) {
        System.out.println("RPC call: recognizeName");
        try {
            String name = this.speechRecog.getName(request.getTimeoutMs(), request.getRetryCount());
            RecognizeNameResponse.Builder responseBuilder = RecognizeNameResponse.newBuilder();
            if (name != null) {
                responseBuilder.setName(name);
            }
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * <pre>
     * 複数の名前候補を認識する (getNames)
     * </pre>
     */
    @Override
    public void recognizeNames(RecognizeNamesRequest request, StreamObserver<RecognizeNamesResponse> responseObserver) {
        System.out.println("RPC call: recognizeNames");
        try {
            String[] names = this.speechRecog.getNames(request.getTimeoutMs(), request.getRetryCount());
            RecognizeNamesResponse.Builder responseBuilder = RecognizeNamesResponse.newBuilder();
            if (names != null) {
                responseBuilder.addAllNames(Lists.newArrayList(names));
            }
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * <pre>
     * 一般的な応答を認識する (getResponse)
     * </pre>
     */
    @Override
    public void recognizeGeneralResponse(RecognizeGeneralResponseRequest request, StreamObserver<RecognizeGeneralResponseResponse> responseObserver) {
        System.out.println("RPC call: recognizeGeneralResponse");
        try {
            String responseStr = this.speechRecog.getResponse(request.getTimeoutMs(), request.getRetryCount());
            RecognizeGeneralResponseResponse.Builder responseBuilder = RecognizeGeneralResponseResponse.newBuilder();
            if(responseStr != null) {
                responseBuilder.setResponse(responseStr);
            }
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * Helper method to convert SotaLib's RecogResult to gRPC's RecognitionResult.
     */
    private RecognitionResult toGrpcRecognitionResult(SpeechRecog.RecogResult nativeResult) {
        if (nativeResult == null) {
            return RecognitionResult.newBuilder().setRecognized(false).build();
        }

        RecognitionResult.Builder grpcResultBuilder = RecognitionResult.newBuilder()
                .setRecognized(nativeResult.recognized)
                .setBasicResult(nativeResult.basicresult != null ? nativeResult.basicresult : "");

        if (nativeResult.Sentencelist != null) {
            for (SpeechRecog.Sentence nativeSentence : nativeResult.Sentencelist) {
                Sentence.Builder grpcSentenceBuilder = Sentence.newBuilder()
                        .setScore(nativeSentence.score);

                if (nativeSentence.wordlist != null) {
                    for (SpeechRecog.Word nativeWord : nativeSentence.wordlist) {
                        Word.Builder grpcWordBuilder = Word.newBuilder();
                        if (nativeWord.Labels != null) {
                            grpcWordBuilder.addAllLabels(Lists.newArrayList(nativeWord.Labels));
                        }
                        if (nativeWord.types != null) {
                            grpcWordBuilder.addAllTypes(Lists.newArrayList(nativeWord.types));
                        }
                        grpcSentenceBuilder.addWordList(grpcWordBuilder.build());
                    }
                }
                grpcResultBuilder.addSentenceList(grpcSentenceBuilder.build());
            }
        }
        return grpcResultBuilder.build();
    }
}
