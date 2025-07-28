package net.keimag.sotagrpc;

import io.grpc.ServerBuilder;
import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;

import java.awt.Color;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MotionDirectTest {

    public static void main(String[] args) throws IOException, InterruptedException {

        // --- 1. ロボットの初期化 ---
        System.out.println("Initializing Sota...");
        CRobotMem mem = new CRobotMem();
        if (!mem.Connect()) {
            System.err.println("Failed to connect to Sota. Exiting.");
            return;
        }
        CSotaMotion motion = new CSotaMotion(mem);
        motion.InitRobot_Sota();
        System.out.println("Sota initialized.");
        System.out.println("------------------------------------------");


        // --- 2. gRPCサーバーを先に起動 ---
        BlockingQueue<Main.SotaTask<?>> commandQueue = new LinkedBlockingQueue<>();
        System.out.println("Starting gRPC server...");
        io.grpc.Server server = ServerBuilder.forPort(8080)
                // MotionServiceImplの中身は、このテストでは使われないので仮でOK
                .addService(new MotionServiceImpl(commandQueue))
                .build();
        server.start();
        System.out.println("Server started on port 8080.");
        System.out.println("------------------------------------------");


        // --- 3. サーバー起動後に、少し待機 ---
        System.out.println("Waiting for 2 seconds to ensure server is fully active...");
        Thread.sleep(2000);
        System.out.println("------------------------------------------");


        // --- 4. サーバーが起動した状態で、mainスレッドからロボットを操作 ---
        System.out.println("Attempting to move robot from the main thread while server is active...");

        try {
            System.out.println("Servo On");
            motion.ServoOn();

            CRobotPose pose = new CRobotPose();
            pose.SetPose(new Byte[]{1, 2, 3, 4, 5, 6, 7, 8}
                    , new Short[]{0, -900, 0, 900, 0, 0, 0, 0});
            pose.setLED_Sota(Color.RED, Color.RED, 255, Color.RED);
            System.out.println("Playing motion 1...");
            boolean success1 = motion.play(pose, 1000);
            System.out.println("  -> Play command returned: " + success1);
            motion.waitEndinterpAll();
            System.out.println("  -> Motion 1 finished.");

            pose = new CRobotPose();
            pose.SetPose(new Byte[]{CSotaMotion.SV_HEAD_R, CSotaMotion.SV_L_SHOULDER, CSotaMotion.SV_L_ELBOW, CSotaMotion.SV_R_ELBOW}, new Short[]{200, 700, -200, 200});
            pose.setLED_Sota(Color.GREEN, Color.GREEN, 255, Color.GREEN);
            System.out.println("Playing motion 2...");
            boolean success2 = motion.play(pose, 1000);
            System.out.println("  -> Play command returned: " + success2);
            motion.waitEndinterpAll();
            System.out.println("  -> Motion 2 finished.");

            // 最後のポーズはログが見やすいように省略

            System.out.println("Servo Off");
            motion.ServoOff();

            System.out.println("Robot control sequence finished.");

        } catch (Exception e) {
            System.err.println("An exception occurred during robot motion.");
            e.printStackTrace();
        } finally {
            System.out.println("------------------------------------------");
            System.out.println("Shutting down gRPC server.");
            server.shutdown();
        }
    }
}