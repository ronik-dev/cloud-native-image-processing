import uvicorn
import os
from fastapi import FastAPI
from dotenv import load_dotenv

load_dotenv()

app = FastAPI()

@app.get("/")
async def root():
    return {"message": "Hello World"}

if __name__ == "__main__":
    port = int(os.getenv("WORKER_PORT", "8082"))
    uvicorn.run(app, host="0.0.0.0", port=port)
