package net.keimag.sotagrpc;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import  net.keimag.sotagrpc.v1.robotlib.*;
import  jp.vstone.RobotLib.CRecordMic;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class RecordingServiceImpl extends RecordingServiceGrpc.RecordingServiceImplBase {
    private final CRecordMic recordMic;
    public static final String TMP_AUDIO_FILENAME = "tmp_Sota-gRPC_recording.wav";

    public RecordingServiceImpl(CRecordMic recordMic) {
        this.recordMic = recordMic;
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void startRecording(StartRecordingRequest request, StreamObserver<StartRecordingResponse> responseObserver) {
        System.out.println("RPC call: startRecording");
        try {
            boolean is_success =  recordMic.startRecording(TMP_AUDIO_FILENAME, request.getDurationMs());
            responseObserver.onNext(StartRecordingResponse.newBuilder().setSuccess(is_success).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void stopRecording(StopRecordingRequest request, StreamObserver<StopRecordingResponse> responseObserver) {
        System.out.println("RPC call: stopRecording");
        try {
            recordMic.stopRecording();
            Path filePath = Paths.get(TMP_AUDIO_FILENAME);
            if (!Files.exists(filePath)) {
                responseObserver.onError(new FileNotFoundException(TMP_AUDIO_FILENAME + " does not exist."));
            } else {
                // TMP_AUDIO_FILENAMEからwavファイルのバイトデータを読みだして，StopRecordingResponseのaudioDataにbytesとして入れる．そのあとオーディオファイルを削除する．
                byte[] audio_data = Files.readAllBytes(filePath);
                Files.delete(filePath);
                responseObserver.onNext(StopRecordingResponse.newBuilder().setAudioData(ByteString.copyFrom(audio_data)).build());
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void isRecording(IsRecordingRequest request, StreamObserver<IsRecordingResponse> responseObserver) {
        System.out.println("RPC call: isRecording");
        responseObserver.onNext(IsRecordingResponse.newBuilder().setIsRecording(this.recordMic.isRacoding()).build());
        responseObserver.onCompleted();
    }
}
