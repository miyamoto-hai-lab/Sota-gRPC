import argparse
import time

import grpc

# 自動生成されたgRPCコードをインポート
from sotagrpc.v1 import (
    robotlib_pb2,
    robotlib_pb2_grpc,
    sotatalk_pb2,
    sotatalk_pb2_grpc,
)


def main(host, port):
    """全サービスのテストを実行するメイン関数"""
    with grpc.insecure_channel(f'{host}:{port}') as channel:
        print(f"Connecting to gRPC server at {host}:{port}...")
        
        # 各サービスのスタブを作成
        motion_stub = robotlib_pb2_grpc.MotionServiceStub(channel)
        playback_stub = robotlib_pb2_grpc.PlaybackServiceStub(channel)
        speech_recog_stub = sotatalk_pb2_grpc.SpeechRecognitionServiceStub(channel)
        tts_stub = sotatalk_pb2_grpc.TextToSpeechServiceStub(channel)
        
        # 各テストを実行
        motion_service_test(motion_stub)
        
        # TTSで音声生成 -> Playbackで再生
        generated_audio = text_to_speech_service_test(tts_stub)
        playback_service_test(playback_stub, generated_audio)
        
        # request = robotlib_pb2.PlayLocalAudioRequest(local_filepath="SotaRap.wav", wait_for_completion=True)
        # response = playback_stub.PlayLocalAudio(request)
        # print(f"PlayLocalAudio {response}")
        
        # speech_recognition_service_test(speech_recog_stub)

        print("\n--- All tests finished ---")


def motion_service_test(stub: robotlib_pb2_grpc.MotionServiceStub):
    """MotionServiceの基本的なテスト"""
    print("\n--- Testing MotionService ---")
    try:
        print("Calling ServoOn...")
        stub.ServoOn(robotlib_pb2.ServoOnRequest())
        print("ServoOn successful.")
        
        banzai_pose = robotlib_pb2.Pose(servos=[
            # robotlib_pb2.Servo(id=robotlib_pb2.L_SHOULDER, angle=-900),
            # robotlib_pb2.Servo(id=robotlib_pb2.R_SHOULDER, angle=900),
            robotlib_pb2.Servo(id=1, angle=0),
            robotlib_pb2.Servo(id=2, angle=-900),
            robotlib_pb2.Servo(id=3, angle=0),
            robotlib_pb2.Servo(id=4, angle=900),
            robotlib_pb2.Servo(id=5, angle=0),
            robotlib_pb2.Servo(id=6, angle=0),
            robotlib_pb2.Servo(id=7, angle=0),
            robotlib_pb2.Servo(id=8, angle=0),
        ])
        request = robotlib_pb2.PlayPoseRequest(pose=banzai_pose, time_ms=1000)
        response = stub.PlayPose(request)
        print(f"PlayPose sent. {response}")
        time.sleep(1) # モーション完了を待つ
        

        # 大胆なポーズ（バンザイ）
        print("Calling PlayPose (reset)...")
        color = robotlib_pb2.Color(red=255, blue=255, green=255)
        led_state = robotlib_pb2.LedState(left_eye=color, right_eye=color, mouth=256, power_button=color)
        banzai_pose = robotlib_pb2.Pose(servos=[
            robotlib_pb2.Servo(id=robotlib_pb2.HEAD_R, angle=0),
            # robotlib_pb2.Servo(id=robotlib_pb2.HEAD_Y, angle=0),
            # robotlib_pb2.Servo(id=robotlib_pb2.HEAD_P, angle=0),
            # robotlib_pb2.Servo(id=robotlib_pb2.BODY_Y, angle=0),
        ],led=led_state)
        request = robotlib_pb2.PlayPoseRequest(pose=banzai_pose, time_ms=1000)
        response = stub.PlayPose(request)
        print(f"PlayPose sent. {response}")
        # motion.wait_for_completion()
        time.sleep(1) # モーション完了を待つ

        # 元のポーズに戻す
        print("Calling PlayPose (kasige)...")
        reset_pose = robotlib_pb2.Pose(servos=[
            robotlib_pb2.Servo(id=robotlib_pb2.HEAD_R, angle=-200),
            # robotlib_pb2.Servo(id=robotlib_pb2.HEAD_Y, angle=90),
            # robotlib_pb2.Servo(id=robotlib_pb2.HEAD_P, angle=90),
            # robotlib_pb2.Servo(id=robotlib_pb2.BODY_Y, angle=90),
        ])
        request = robotlib_pb2.PlayPoseRequest(pose=reset_pose, time_ms=500)
        response = stub.PlayPose(request)
        print(f"PlayPose sent. {response}")
        time.sleep(0.5)
        
        # 大胆なポーズ（バンザイ）
        print("Calling PlayPose (reset)...")
         # color = robotlib_pb2.Color(red=255, blue=255, green=255)
        # led_state = robotlib_pb2.LedState(left_eye=color, right_eye=color, mouth=color, power_button=color)
        banzai_pose = robotlib_pb2.Pose(servos=[
            robotlib_pb2.Servo(id=robotlib_pb2.HEAD_R, angle=0),
            # robotlib_pb2.Servo(id=robotlib_pb2.HEAD_Y, angle=0),
            # robotlib_pb2.Servo(id=robotlib_pb2.HEAD_P, angle=0),
            # robotlib_pb2.Servo(id=robotlib_pb2.BODY_Y, angle=0),
        ])
        request = robotlib_pb2.PlayPoseRequest(pose=banzai_pose, time_ms=1000)
        stub.PlayPose(request)
        print("PlayPose sent.")
        # motion.wait_for_completion()
        time.sleep(2) # モーション完了を待つ

        # サーボオフ
        print("Calling ServoOff...")
        stub.ServoOff(robotlib_pb2.ServoOffRequest())
        print("ServoOff successful.")

    except grpc.RpcError as e:
        print(f"An RPC error occurred in MotionService: {e.details()}")


def text_to_speech_service_test(stub: sotatalk_pb2_grpc.TextToSpeechServiceStub) -> bytes | None:
    """TextToSpeechServiceのテストを行い、生成した音声データを返す"""
    print("\n--- Testing TextToSpeechService ---")
    try:
        text_to_say = "こんにちは、ソータです。Python gRPC経由で再生中。"
        print(f"Calling Synthesize with text: '{text_to_say}'")
        speech_config = sotatalk_pb2.SpeechConfig(pitch=8, intonation=8, speech_rate=8)
        request = sotatalk_pb2.TTSDataRequest(text=text_to_say, config=speech_config)
        response = stub.GetTTSData(request)
        if response.audio_data:
            print(f"Synthesize successful. Got {len(response.audio_data)} bytes of audio data.")
            return response.audio_data
        else:
            print("Synthesize failed.")
            return None

    except grpc.RpcError as e:
        print(f"An RPC error occurred in TextToSpeechService: {e.details()}")
        return None

def playback_service_test(stub: robotlib_pb2_grpc.PlaybackServiceStub, audio_data: bytes | None):
    """PlaybackServiceのテスト。受け取った音声データを再生する"""
    print("\n--- Testing PlaybackService ---")
    if not audio_data:
        print("No audio data received from TTS. Skipping test.")
        return
        
    try:
        print(f"Calling PlayAudio with synthesized data...")
        request = robotlib_pb2.PlayAudioRequest(audio_data=audio_data, wait_for_completion=True)
        response = stub.PlayAudio(request)
        print(f"PlayAudio successful: {response.success}")

    except grpc.RpcError as e:
        print(f"An RPC error occurred in PlaybackService: {e.details()}")


def speech_recognition_service_test(stub: sotatalk_pb2_grpc.SpeechRecognitionServiceStub):
    """SpeechRecognitionServiceの基本的なテスト (ユーザーの操作が必要)"""
    print("\n--- Testing SpeechRecognitionService ---")
    try:
        # 名前認識のテスト
        print("Calling RecognizeName... Please say a name within 5 seconds.")
        request = sotatalk_pb2.RecognizeRequest(timeout_ms=10000)
        response = stub.Recognize(request)
        print(f"{response.recognized=}, {response.basic_result=}, {response.sentence_list}'")

    except grpc.RpcError as e:
        print(f"An RPC error occurred in SpeechRecognitionService: {e.details()}")

if __name__ == '__main__':
    argparser = argparse.ArgumentParser(
        prog="python -m connection_test",
        description="Sota gRPC test client",
        epilog="Example: python -m connection_test 192.168.1.10 --port 8080")
    argparser.add_argument('host', type=str, help="Sota's IP address")
    argparser.add_argument('--port', '-p', type=int, help="Sota's listening port (default: 8080)")
    args = argparser.parse_args()
    if ':' in args.host:
        host = args.host.split(':')[0]
        port = int(args.port if args.port else args.host.split(':')[1])
    else:
        host = args.host
        port = int(args.port if args.port else 8080)
    main(host, port)
