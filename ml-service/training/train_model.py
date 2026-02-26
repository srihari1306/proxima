import pandas as pd
import numpy as np
from xgboost import XGBRegressor
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score
import joblib
import matplotlib.pyplot as plt

DATA_PATH = "../../load-balancer/train.csv"

def load_data(path):
    df = pd.read_csv(path)
    
    df.columns = df.columns.str.strip()
    print(df.columns.tolist())
    df = df[df["success"] == True]

    features = [
        "active_connections",
        "avg_latency",
        "last_latency",
        "request_rate"
    ]

    target = "observed_latency"

    X = df[features]
    y = df[target]

    return X, y


def train_model(X, y):
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    model = XGBRegressor(
        n_estimators = 200,
        max_depth = 6,
        learning_rate = 0.1,
        subsample = 0.8,
        random_state = 42
    )

    print("Training model...")
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)

    mae = mean_absolute_error(y_test, y_pred)
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))
    r2 = r2_score(y_test, y_pred)

    print("\n=== Model Performance ===")
    print(f"MAE:  {mae:.2f} ms")
    print(f"RMSE: {rmse:.2f} ms")
    print(f"R²:   {r2:.4f}")

    print("\nRunning 5-fold cross validation...")
    cv_scores = cross_val_score(
        model,
        X,
        y,
        cv=5,
        scoring="neg_mean_absolute_error"
    )

    print(f"CV MAE: {-cv_scores.mean():.2f} ± {cv_scores.std():.2f} ms")

    print("\n=== Feature Importance ===")
    for feature, importance in zip(X.columns, model.feature_importances_):
        print(f"{feature:20s}: {importance:.4f}")

    joblib.dump(model, "../models/latency_predictor.pkl")
    print("\nModel saved to ml-service/models/latency_predictor.pkl")

    return y_test, y_pred

def plot_predictions(y_test, y_pred):
    plt.figure(figsize=(8, 6))
    plt.scatter(y_test, y_pred, alpha=0.3)
    plt.plot(
        [y_test.min(), y_test.max()],
        [y_test.min(), y_test.max()],
        "r--"
    )
    plt.xlabel("Actual Latency (ms)")
    plt.ylabel("Predicted Latency (ms)")
    plt.title("Prediction Accuracy")
    plt.tight_layout()
    plt.savefig("../models/prediction_plot.png")
    print("Prediction plot saved.")

def main():
    print("Loading dataset...")
    X, y = load_data(DATA_PATH)

    print(f"Dataset size: {len(X)} rows")
    print(f"Latency range: {y.min():.0f} - {y.max():.0f} ms")

    y_test, y_pred = train_model(X, y)
    plot_predictions(y_test, y_pred)

    print("\nTraining complete.")


if __name__ == "__main__":
    main()