package net.keimag.sotagrpc;

import io.grpc.stub.StreamObserver;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import net.keimag.sotagrpc.v1.robotlib.*;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

public class MotionServiceImpl extends MotionServiceGrpc.MotionServiceImplBase {

    private final BlockingQueue<Main.SotaTask<?>> commandQueue;

    private static final String TAG = "Sota-gRPC.MotionService";

    public MotionServiceImpl(BlockingQueue<Main.SotaTask<?>> commandQueue) {
        this.commandQueue = commandQueue;
    }

    /**
     * <pre>
     * サーボモーターのトルクをオンにする (ServoOn)
     * </pre>
     */
    @Override
    public void servoOn(ServoOnRequest request, StreamObserver<ServoOnResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: servoOn");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<Void>(
                    (sotaContext) -> {
                        sotaContext.motion.ServoOn();
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            ServoOnResponse response = ServoOnResponse.newBuilder().build();
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
     * サーボモーターのトルクをオフにする (ServoOff)
     * </pre>
     */
    @Override
    public void servoOff(ServoOffRequest request, StreamObserver<ServoOffResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: servoOff");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        sotaContext.motion.ServoOff();
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            ServoOffResponse response = ServoOffResponse.newBuilder().build();
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
     * 指定したポーズを再生する (play)
     * </pre>
     */
    @Override
    public void playPose(PlayPoseRequest request, StreamObserver<PlayPoseResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: playPose");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        CRobotPose pose = new CRobotPose();
                        ArrayList<Byte> ids = new ArrayList<>();
                        ArrayList<Short> pos = new ArrayList<>();
                        int time = request.getTimeMs();
                        Pose requestedPose = request.getPose();
                        Servo[] servos = requestedPose.getServosList().toArray(new Servo[0]);
                        for (Servo servo : servos) {
                            if (servo.getId() != ServoID.SERVO_ID_UNSPECIFIED) {
                                ids.add((byte) servo.getId().getNumber());
                                pos.add((short) servo.getAngle());
                            }
                        }
                        pose.SetPose(ids.toArray(new Byte[0]), pos.toArray(new Short[0]));
                        if (requestedPose.hasLed()) {
                            LedState led = requestedPose.getLed();
                            pose.setLED_Sota(toAwtColor(led.getLeftEye()), toAwtColor(led.getRightEye()), led.getMouth(), toAwtColor(led.getPowerButton()));
                        }
                        return sotaContext.motion.play(pose, time);
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            boolean success = future.get(); // 処理結果が来るまで待機
            PlayPoseResponse response = PlayPoseResponse.newBuilder().setSuccess(success).build();
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
     * 現在のサーボ角度（ポーズ）を取得する (getReadPose)
     * </pre>
     */
    @Override
    public void getCurrentPose(GetCurrentPoseRequest request, StreamObserver<Pose> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: getCurrentPose");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Pose> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        Short[] angles = sotaContext.motion.getReadpos();
                        Byte[] ids = sotaContext.motion.getDefaultIDs();

                        if (angles == null || ids == null || ids.length != angles.length) {
                            throw new IllegalStateException("Failed to read servo positions.");
                        }

                        Pose.Builder poseBuilder = Pose.newBuilder();
                        for (int i = 0; i < ids.length; i++) {
                            Servo servo = Servo.newBuilder()
                                    .setId(ServoID.forNumber(ids[i]))
                                    .setAngle(angles[i])
                                    .build();
                            poseBuilder.addServos(servo);
                        }
                        return poseBuilder.build();
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            Pose pose = future.get(); // 処理結果が来るまで待機
            responseObserver.onNext(pose);
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
     * モーション再生が完了したか確認する (isEndinterpAll)
     * </pre>
     */
    @Override
    public void isEndInterAll(IsEndInterAllRequest request, StreamObserver<IsEndInterAllResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: isEndInterAll");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        return sotaContext.motion.isEndInterpAll();
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            boolean isEndInterAll = future.get(); // 処理結果が来るまで待機
            IsEndInterAllResponse response = IsEndInterAllResponse.newBuilder().setIsEndInterAll(isEndInterAll).build();
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
     * 電源関連の状態を取得する (getBatteryVoltage, isChargingなど)
     * </pre>
     */
    @Override
    public void getPowerStatus(GetPowerStatusRequest request, StreamObserver<GetPowerStatusResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: getPowerStatus");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<GetPowerStatusResponse> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        int voltage = sotaContext.motion.getBatteryVoltage();
                        boolean isCharging = sotaContext.motion.isCharging();
                        return GetPowerStatusResponse.newBuilder()
                                .setBatteryVoltageMv(voltage)
                                .setIsCharging(isCharging)
                                .build();
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            GetPowerStatusResponse response = future.get(); // 処理結果が来るまで待機
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
     * 本体のボタンの状態を取得する (isButton_***)
     * </pre>
     */
    @Override
    public void getButtonState(GetButtonStateRequest request, StreamObserver<GetButtonStateResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: getButtonState");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<GetButtonStateResponse> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        boolean isPowerPressed = sotaContext.motion.isButton_Power();
                        boolean isVolUpPressed = sotaContext.motion.isButton_VolUp();
                        boolean isVolDownPressed = sotaContext.motion.isButton_VolDown();

                        GetButtonStateResponse response = GetButtonStateResponse.newBuilder()
                                .setIsPowerPressed(isPowerPressed)
                                .setIsVolUpPressed(isVolUpPressed)
                                .setIsVolDownPressed(isVolDownPressed)
                                .build();
                        return response;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            GetButtonStateResponse response = future.get(); // 処理結果が来るまで待機
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
     * 衝突検知を無効にする (DisableCollisionDetection)
     * </pre>
     */
    @Override
    public void setCollisionDetection(SetCollisionDetectionRequest request, StreamObserver<SetCollisionDetectionResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: setCollisionDetection");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        if (request.getEnabled()) {
                            sotaContext.motion.EnableCollidionDetect();
                        } else {
                            sotaContext.motion.DisableCollidionDetect();
                        }
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            SetCollisionDetectionResponse response = SetCollisionDetectionResponse.newBuilder().build();
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
     * 口のLEDと音声の同期を有効/無効にする (enabe/disabeMouthLEDVoiceSync)
     * </pre>
     */
    @Override
    public void setMouthLedVoiceSync(SetMouthLedVoiceSyncRequest request, StreamObserver<SetMouthLedVoiceSyncResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: setMouthLedVoiceSync");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<>(
                    (sotaContext) -> {
                        if (request.getEnabled()) {
                            sotaContext.motion.enabeMouthLEDVoiceSync();
                        } else {
                            sotaContext.motion.disabeMouthLEDVoiceSync();
                        }
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            SetMouthLedVoiceSyncResponse response = SetMouthLedVoiceSyncResponse.newBuilder().build();
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

    // --- Helper Methods for type conversion ---
    private Color toAwtColor(net.keimag.sotagrpc.v1.robotlib.Color grpcColor) {
        return new Color(grpcColor.getRed(), grpcColor.getGreen(), grpcColor.getBlue());
    }
}
