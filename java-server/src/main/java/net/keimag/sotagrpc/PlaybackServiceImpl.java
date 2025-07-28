package net.keimag.sotagrpc;

import io.grpc.stub.StreamObserver;
import jp.vstone.RobotLib.CPlayWave;
import net.keimag.sotagrpc.v1.robotlib.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlaybackServiceImpl extends PlaybackServiceGrpc.PlaybackServiceImplBase {

    // 実行中のCPlayWaveインスタンスをスレッドセーフに管理するMap
    // Key: playback_id, Value: CPlayWave instance
    private final ConcurrentMap<String, CPlayWave> activePlayers = new ConcurrentHashMap<>();

    /**
     * <pre>
     * 音声データ(wav)を再生し、再生IDを返す
     * </pre>
     */
    @Override
    public void playAudio(PlayAudioRequest request, StreamObserver<PlayAudioResponse> responseObserver) {
        System.out.println("RPC call: playAudio");
        try {
            byte[] audioData = request.getAudioData().toByteArray();
            boolean waitForCompletion = request.getWaitForCompletion();

            // 再生を一意に識別するためのIDを生成
            String playbackId = UUID.randomUUID().toString();

            // CPlayWave.PlayWaveを呼び出し
            CPlayWave player = CPlayWave.PlayWave(audioData, waitForCompletion);

            boolean success = (player != null);
            if (success && !waitForCompletion) {
                // 非同期再生の場合のみ、後から操作できるようにMapに保存
                activePlayers.put(playbackId, player);
            }

            PlayAudioResponse response = PlayAudioResponse.newBuilder()
                    .setSuccess(success)
                    .setPlaybackId(playbackId)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
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
            responseObserver.onNext(StopAudioResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
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

            IsAudioPlayingResponse response = IsAudioPlayingResponse.newBuilder().setIsPlaying(isPlaying).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
