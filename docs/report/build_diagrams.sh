#!/bin/bash
mkdir -p images
for file in diagrams/*.mmd; do
    filename=$(basename "$file" .mmd)
    mmdc -i "$file" -o "images/$filename.pdf" -f
done
