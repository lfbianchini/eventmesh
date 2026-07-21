FROM python:3.13-alpine

RUN addgroup -S subscriber && adduser -S subscriber -G subscriber

WORKDIR /app
COPY server.py /app/server.py

USER subscriber
EXPOSE 8090

CMD ["python", "/app/server.py"]
