### Test
``` Bash
kubectl exec -it kafka-0 -n imageprocessing -- /bin/bash -c "
for i in {1..30}; do
  echo '{\"job_id\": \"scale-test-'$i'\", \"job_type\": \"BACKGROUND_REMOVAL\", \"source_sk\": \"8cef3e8b-7c2e-49d4-a233-2b49c9d7c633\", \"target_sk\": \"scale-out-'$i'.png\"}'
done | /opt/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic job.requests
"
````
