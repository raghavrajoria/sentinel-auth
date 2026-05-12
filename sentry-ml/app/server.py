import grpc
import json
import numpy as np
import xgboost as xgb
from concurrent import futures
import scoring_pb2
import scoring_pb2_grpc

# Load model and features once at startup
MODEL_PATH = '../models/xgboost_sentinel.json'
FEATURES_PATH = '../models/features.json'

model = xgb.XGBClassifier()
model.load_model(MODEL_PATH)

with open(FEATURES_PATH) as f:
    FEATURES = json.load(f)

print(f"Model loaded. Features: {len(FEATURES)}")
print(f"Top features: {FEATURES[:5]}")

class ScoringService(scoring_pb2_grpc.ScoringServiceServicer):

    def Score(self, request, context):
        try:
            print(f"Received request: tx_id={request.tx_id}, amount={request.amount}")
            
            feature_values = build_features(
                amount=request.amount,
                history_count=request.history_count,
                user_id=request.user_id
            )
            
            # XGBoost inference
            proba = model.predict_proba([feature_values])[0][1]
            risk_score_value = int(proba * 100)
            
            print(f"Scored tx={request.tx_id} proba={proba:.3f} risk={risk_score_value}")
            
            # CORRECTED: Ensure the keyword argument matches the .proto definition exactly
            # If your proto has 'int32 risk_score', Python usually keeps it 'risk_score'
            # But the 'except' block had 'riskScore', which was likely the bug.
            return scoring_pb2.ScoreResponse(risk_score=risk_score_value)

        except Exception as e:
            import traceback
            traceback.print_exc()
            print(f"Scoring error: {e}")
            # Ensure consistency here too:
            return scoring_pb2.ScoreResponse(risk_score=50)


def build_features(amount: float, history_count: int, user_id: str) -> list:
    """
    Map gRPC inputs to the 29-feature vector the model expects.
    Features: C1-C10, addr2, id_03, id_04, V1-V6, TransactionAmt, card1/2, dist1/2, P_emaildomain
    
    For features we don't have from the gRPC call, we use:
    - Sensible defaults based on dataset medians
    - Derived proxies from what we do have
    """
    # Parse a numeric user shard from user_id for card proxy
    user_shard = abs(hash(user_id)) % 10000

    feature_map = {
        'TransactionAmt': amount,
        'card1': user_shard,
        'card2': user_shard % 100,
        'addr1': 299.0,          # dataset median
        'addr2': 87.0,           # dataset median
        'dist1': 0.0,
        'dist2': 0.0,
        'P_emaildomain': 0.0,    # encoded as 0 (unknown)
        'C1': float(history_count),   # count of transactions — we have this
        'C2': float(history_count),
        'C3': 0.0,
        'C4': 0.0,
        'C5': 0.0,
        'C6': float(history_count),
        'C7': 0.0,
        'C8': 0.0,
        'C9': float(history_count > 0),
        'C10': 0.0,
        'V1': 0.0,
        'V2': 0.0,
        'V3': 0.0,
        'V4': 0.0,
        'V5': amount / 1000.0,   # normalized amount proxy
        'V6': 0.0,
        'id_01': 0.0,
        'id_02': float(user_shard),
        'id_03': 0.0,
        'id_04': 0.0,
        'id_05': 0.0,
    }

    # Return in exact order the model was trained on
    return [feature_map.get(f, -999.0) for f in FEATURES]


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    scoring_pb2_grpc.add_ScoringServiceServicer_to_server(ScoringService(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    print("Sentry ML server started on port 50051")
    
    # Warmup — run one inference so first real request is fast
    dummy = build_features(amount=100.0, history_count=1, user_id="warmup")
    model.predict_proba([dummy])
    print("Model warmed up — ready for traffic")
    
    server.wait_for_termination()

if __name__ == '__main__':
    serve()