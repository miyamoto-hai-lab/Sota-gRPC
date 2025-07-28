rem 入力ファイルのパスを新しい構造に合わせる
python -m grpc_tools.protoc -I=proto --python_out=python-client/src --grpc_python_out=python-client/src proto/sotagrpc/v1/*.proto