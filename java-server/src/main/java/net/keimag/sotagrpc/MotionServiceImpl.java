package net.keimag.sotagrpc;

import io.grpc.stub.StreamObserver;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import net.keimag.sotagrpc.v1.robotlib.*;

import java.awt.Color; // <-- java.awt.Colorをインポート
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
            this.commandQueue.put(new Main.SotaTask<Void>(
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
                            ids.add((byte) servo.getId().getNumber());
                            pos.add((short) servo.getAngle());
                        }
                        pose.SetPose(ids.toArray(new Byte[0]), pos.toArray(new Short[0]));
                        if (requestedPose.hasLed()) {
                            LedState led = requestedPose.getLed();
                            pose.setLED_Sota(toAwtColor(led.getLeftEye()), toAwtColor(led.getRightEye()), led.getMouth(), toAwtColor(led.getPowerButton()));
                        }
                        System.out.println(pose);
                        System.out.println(time);
                        boolean is_success = sotaContext.motion.play(pose, time);
                        sotaContext.motion.waitEndinterpAll();
                        return is_success;
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
            this.commandQueue.put(new Main.SotaTask<Pose>(
                    (sotaContext) -> {
                        Short[] angles = sotaContext.motion.getReadpos();
                        Byte[] ids = sotaContext.motion.getDefaultIDs();

                        if (angles == null || ids == null || ids.length != angles.length) {
                            throw new IllegalStateException("Failed to read servo positions.");
                        }

                        Pose.Builder poseBuilder = Pose.newBuilder();
                        for (int i = 0; i < ids.length; i++) {
                            Servo servo = Servo.newBuilder()
                                    .setId(servoIdFromByte(ids[i]))
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
            this.commandQueue.put(new Main.SotaTask<Boolean>(
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
            this.commandQueue.put(new Main.SotaTask<GetPowerStatusResponse>(
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
            this.commandQueue.put(new Main.SotaTask<GetButtonStateResponse>(
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
     * 衝突検知を有効にする (EnableCollisionDetection)
     * </pre>
     */
    @Override
    public void enableCollisionDetection(EnableCollisionDetectionRequest request, StreamObserver<EnableCollisionDetectionResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: enableCollisionDetection");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<Void>(
                    (sotaContext) -> {
                        sotaContext.motion.EnableCollidionDetect();
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            EnableCollisionDetectionResponse response = EnableCollisionDetectionResponse.newBuilder().build();
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
    public void disableCollisionDetection(DisableCollisionDetectionRequest request, StreamObserver<DisableCollisionDetectionResponse> responseObserver) {
        CRobotUtil.Log(TAG, "RPC call: disableCollisionDetection");
        try {
            // 1. 結果を受け取るためのCompletableFutureを作成
            CompletableFuture<Void> future = new CompletableFuture<>();
            // 2. 実行したい処理とFutureをSotaTaskとしてキューに入れる
            this.commandQueue.put(new Main.SotaTask<Void>(
                    (sotaContext) -> {
                        sotaContext.motion.DisableCollidionDetect();
                        return null;
                    }, future
            ));
            // 3. 専用スレッドでの処理が完了し、futureに結果がセットされるまで待機する
            future.get(); // 処理結果が来るまで待機
            DisableCollisionDetectionResponse response = DisableCollisionDetectionResponse.newBuilder().build();
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
            this.commandQueue.put(new Main.SotaTask<Void>(
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

    private byte servoIdToByte(ServoID id) {
        switch (id) {
            case BODY_Y: return 1;
            case L_SHOULDER: return 2;
            case L_ELBOW: return 3;
            case R_SHOULDER: return 4;
            case R_ELBOW: return 5;
            case HEAD_Y: return 6;
            case HEAD_P: return 7;
            case HEAD_R: return 8;
            default: return 0; // Or throw an exception
        }
    }

    private ServoID servoIdFromByte(byte id) {
        switch (id) {
            case 1: return ServoID.BODY_Y;
            case 2: return ServoID.L_SHOULDER;
            case 3: return ServoID.L_ELBOW;
            case 4: return ServoID.R_SHOULDER;
            case 5: return ServoID.R_ELBOW;
            case 6: return ServoID.HEAD_Y;
            case 7: return ServoID.HEAD_P;
            case 8: return ServoID.HEAD_R;
            default: return ServoID.UNRECOGNIZED;
        }
    }

    private Color toAwtColor(net.keimag.sotagrpc.v1.robotlib.Color grpcColor) {
        return new Color(grpcColor.getRed(), grpcColor.getGreen(), grpcColor.getBlue());
    }
}
