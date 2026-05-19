import grpc
import scoring_pb2
import scoring_pb2_grpc
from concurrent import futures

class ShadowScoringService(scoring_pb2_grpc.ScoringServiceServicer):
    def Score(self, request, context):
        # Old dummy model — v1 baseline
        risk_score = min(100, int(request.amount / 100))
        if request.history_count == 0:
            risk_score = min(100, risk_score + 10)
        print(f"Shadow scored tx={request.tx_id} amount={request.amount:.2f} risk={risk_score}")
        return scoring_pb2.ScoreResponse(risk_score=risk_score)

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    scoring_pb2_grpc.add_ScoringServiceServicer_to_server(ShadowScoringService(), server)
    server.add_insecure_port('[::]:50052')
    server.start()
    print("Shadow ML server started on port 50052 (dummy v1 model)")
    server.wait_for_termination()

if __name__ == '__main__':
    serve()