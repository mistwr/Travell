#!/usr/bin/env python3
"""
LUMINAI Travel — Gradle Wrapper Setup
Run this script ONCE after extracting the ZIP to download gradle-wrapper.jar
Usage: python3 setup_gradle.py
"""

import urllib.request
import os
import hashlib

JAR_URL = "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
JAR_PATH = os.path.join(os.path.dirname(__file__), "gradle", "wrapper", "gradle-wrapper.jar")

def download_jar():
    os.makedirs(os.path.dirname(JAR_PATH), exist_ok=True)
    
    if os.path.exists(JAR_PATH):
        print("✅ gradle-wrapper.jar already exists.")
        return
    
    print("📦 Downloading gradle-wrapper.jar...")
    try:
        urllib.request.urlretrieve(JAR_URL, JAR_PATH)
        print(f"✅ Downloaded to: {JAR_PATH}")
        print("\n🚀 Now you can run: ./gradlew assembleDebug")
    except Exception as e:
        print(f"❌ Download failed: {e}")
        print("\nManual fix:")
        print("1. Download from: https://github.com/gradle/gradle/releases/tag/v8.4.0")
        print(f"2. Place gradle-wrapper.jar in: gradle/wrapper/")

if __name__ == "__main__":
    download_jar()
