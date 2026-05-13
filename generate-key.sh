#!/bin/bash
echo "Add this to your .env file as ENCRYPTION_KEY="
openssl rand -base64 32
