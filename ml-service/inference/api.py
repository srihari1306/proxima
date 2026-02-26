from fastapi import FastAPI
from pydantic import BaseModel
import joblib
from typing import List
import numpy as np
import time

app = FastAPI()

model = joblib.load("../models/latency_predictor.pkl")

class BackendMetrics(BaseModel):
    backend_id: str
    active_connections: int
    avg_latency: float
    last_latency: float
    request_rate: float

class PredictionRequest(BaseModel):
    backends: List[BackendMetrics]

class BackendPrediction(BaseModel):
    backend_id: str
    predicted_latency: float

class PredictionResponse(BaseModel):
    predictions: List[BackendPrediction]
    ranked_backends: List[str]
    inference_time_ms: float
    
@app.post("/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest):
    start_time = time.time()

    predictions = []

    for backend in request.backends:
        features = np.array([[
            backend.active_connections,
            backend.avg_latency,
            backend.last_latency,
            backend.request_rate
        ]])

        predicted_latency = float(model.predict(features)[0])
        predictions.append(BackendPrediction(
            backend_id=backend.backend_id,
            predicted_latency=predicted_latency
        ))
    
    ranked = sorted(predictions, key=lambda x: x.predicted_latency)
    ranked_ids = [p.backend_id for p in ranked]

    inference_time = (time.time() - start_time) * 1000

    return PredictionResponse(
        predictions=predictions,
        ranked_backends=ranked_ids,
        inference_time_ms=inference_time
    )

@app.get("/health")
def health():
    return {"status": "healthy"}