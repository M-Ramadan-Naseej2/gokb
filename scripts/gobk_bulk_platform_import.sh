#!/bin/bash

# Configuration
URLPROD="https://gokb.org/gokb/integration/crossReferencePlatform"
URLTEST="https://gokbt.gbv.de/gokb/integration/crossReferencePlatform"
URLQA="https://gokb-qa.hbz-nrw.de/gokb/integration/crossReferencePlatform"
URLDEV="https://gokb-dev.hbz-nrw.de/gokb/integration/crossReferencePlatform"
USERNAME="USERNAME"    
PASSWORD="PASSWORD"    
CSV_FILE="platforms.csv"

# Check if CSV exists
if [[ ! -f "$CSV_FILE" ]]; then
    echo "Error: File $CSV_FILE not found."
    exit 1
fi

# Read CSV without header line by line
tail -n +2 "$CSV_FILE" | while IFS=$'\t' read -r primaryUrl name
do
    # Debug
    echo "Send Data: primaryUrl=$primaryUrl, name=$name"

    # Build JSON and send with CURL
    curl -X POST "$URLQA" \
         -H "Content-Type: application/json" \
         -u "$USERNAME:$PASSWORD" \
         -d "{\"primaryUrl\": \"$primaryUrl\", \"name\": \"$name\"}"

    echo -e "\nRequest has been sent."
done
