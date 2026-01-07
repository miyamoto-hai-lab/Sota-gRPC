package net.keimag.sotagrpc;

import io.grpc.stub.StreamObserver;
import jp.vstone.RobotLib.CPlayWave;
import net.keimag.sotagrpc.v1.robotlib.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlaybackServiceImpl extends PlaybackServiceGrpc.PlaybackServiceImplBase {
    private static final String TMP_AUDIO_FILENAME = "tmp_Sota-gRPC_playAudio.wav";
    private final BlockingQueue<Main.SotaTask<?>> commandQueue;

    // 実行中のCPlayWaveインスタンスをスレッドセーフに管理するMap
    // Key: playback_id, Value: CPlayWave instance
    private final ConcurrentMap<String, CPlayWave> activePlayers = new ConcurrentHashMap<>();

    public PlaybackServiceImpl(BlockingQueue<Main.SotaTask<?>> commandQueue) {
        this.commandQueue = commandQueue;
    }

    /**
     * <pre>
     * 音声データ(wav)を再生し、再生IDを返す
     * </pre>
     */
    @Override
    public void playAudio(PlayAudioRequest request, StreamObserver<PlayAudioResponse> responseObserver) {
        System.out.println("RPC call: playAudio");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<PlayAudioResponse> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (v) -> {
                        byte[] audioData = request.getAudioData().toByteArray();
                        boolean waitForCompletion = false;
                        boolean saveFile = false;
                        if (request.hasWaitForCompletion()) {
                            waitForCompletion = request.getWaitForCompletion();
                        }
                        if (request.hasSaveFile()) {
                            saveFile = request.getSaveFile();
                        }

                        // 再生を一意に識別するためのIDを生成
                        String playbackId = UUID.randomUUID().toString();


                        // CPlayWave.PlayWaveを呼び出し
                        CPlayWave player = null;
                        if (saveFile) {
                            // audioDataをTMP_AUDIO_FILENAMEに保存する．
                            try {
                                // バイト配列をファイルに書き込む
                                Files.write(Paths.get(TMP_AUDIO_FILENAME), audioData);
//                    System.out.println("音声ファイルが正常に保存されました: " + path.toAbsolutePath());
                                player = CPlayWave.PlayWave(TMP_AUDIO_FILENAME, waitForCompletion);
                            } catch (IOException e) {
//                    System.err.println("ファイルの書き込み中にエラーが発生しました。");
                                e.printStackTrace();
                            }
                        } else {
                            player = CPlayWave.PlayWave(audioData, waitForCompletion);
                        }

                        boolean success = (player != null);
                        if (success && !waitForCompletion) {
                            // 非同期再生の場合のみ、後から操作できるようにMapに保存
                            activePlayers.put(playbackId, player);
                        }

                        return PlayAudioResponse.newBuilder()
                                .setSuccess(success)
                                .setPlaybackId(playbackId)
                                .build();
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            PlayAudioResponse response = future.get(); // 処理結果が来るまで待機
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
     * Sotaローカルに保存された音声データ(wav)を再生し、再生IDを返す
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void playLocalAudio(PlayLocalAudioRequest request, StreamObserver<PlayAudioResponse> responseObserver) {
        System.out.println("RPC call: playLocalAudio");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<PlayAudioResponse> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (v) -> {
                        String audioFilePath = request.getLocalFilepath();
                        boolean waitForCompletion;
                        if (request.hasWaitForCompletion()) {
                            waitForCompletion = request.getWaitForCompletion();
                        } else {
                            waitForCompletion = false;
                        }

                        // 再生を一意に識別するためのIDを生成
                        String playbackId = UUID.randomUUID().toString();

                        // CPlayWave.PlayWaveを呼び出し
                        CPlayWave player = CPlayWave.PlayWave(audioFilePath, waitForCompletion);

                        boolean success = (player != null);
                        if (success && !waitForCompletion) {
                            // 非同期再生の場合のみ、後から操作できるようにMapに保存
                            activePlayers.put(playbackId, player);
                        }

                        return PlayAudioResponse.newBuilder()
                                .setSuccess(success)
                                .setPlaybackId(playbackId)
                                .build();
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            PlayAudioResponse response = future.get(); // 処理結果が来るまで待機
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            System.out.println("Finish playLocalAudio");
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
     * 指定したIDの音声再生を停止する
     * </pre>
     */
    @Override
    public void stopAudio(StopAudioRequest request, StreamObserver<StopAudioResponse> responseObserver) {
        System.out.println("RPC call: stopAudio");
        try {
            CompletableFuture<Void> future = new CompletableFuture<>();
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext -> {
                        if (request.hasPlaybackId()) {
                            // 特定のIDの再生を停止
                            String playbackId = request.getPlaybackId();
                            CPlayWave player = activePlayers.remove(playbackId); // 取得と同時にMapから削除
                            if (player != null) {
                                player.stop();
                            }
                        } else {
                            // 全ての再生を停止
                            activePlayers.values().forEach(CPlayWave::stop);
                            activePlayers.clear();
                        }
                        return null;
                    }), future
            ));
            future.get();
            responseObserver.onNext(StopAudioResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            e.printStackTrace();
            responseObserver.onError(e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    /**
     * <pre>
     * 指定したIDの音声が再生中か確認する
     * </pre>
     */
    @Override
    public void isAudioPlaying(IsAudioPlayingRequest request, StreamObserver<IsAudioPlayingResponse> responseObserver) {
        System.out.println("RPC call: isAudioPlaying");
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext -> {
                        boolean isPlaying = false;
                        if (request.hasPlaybackId()) {
                            // 特定のIDの再生状態を確認
                            String playbackId = request.getPlaybackId();
                            CPlayWave player = activePlayers.get(playbackId);
                            if (player != null) {
                                isPlaying = player.isPlaying();
                                // もし再生が終わっていたらMapから削除（クリーンアップ）
                                if (!isPlaying) {
                                    activePlayers.remove(playbackId);
                                }
                            }
                        } else {
                            // いずれかの音声が再生中か確認
                            // 再生が終わったものを掃除しながら確認
                            activePlayers.entrySet().removeIf(entry -> !entry.getValue().isPlaying());
                            isPlaying = !activePlayers.isEmpty();
                        }
                        return isPlaying;
                    }), future
            ));
            boolean isPlaying = future.get();
            IsAudioPlayingResponse response = IsAudioPlayingResponse.newBuilder().setIsPlaying(isPlaying).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            e.printStackTrace();
            responseObserver.onError(e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }
}
