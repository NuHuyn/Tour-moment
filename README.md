Here is a professional **README.md** written in English, tailored for your GitHub repository and project submission.

---

# 🌍 MyCurrentTour - Travel Itinerary Management Platform

**MyCurrentTour** is a mobile application ecosystem designed to help users track waypoints, manage travel expenses, and preserve memories through photos. The system features a native Android client integrated with a robust Node.js backend deployed on Google Cloud.

## 🚀 Tech Stack

### 📱 Mobile Client (Frontend)
* **Language:** Java
* **Development Tool:** Android Studio
* **Networking:** Retrofit for REST API communication
* **Distribution:** Accessible via `.apk` (Android Package) file

### 🖥️ Backend Service
* **Runtime:** Node.js (Express.js Framework)
* **Database:** MongoDB
* **Architecture:** Microservices-oriented design managed via Docker

### ☁️ Infrastructure & DevOps
* **Cloud Provider:** Google Cloud Platform (GCP)
* **Containerization:** Docker & Docker Compose (orchestrating Backend and Database services)
* **Reverse Proxy:** Nginx (handling traffic on port 80 and forwarding to the Node.js container)
* **Networking:** Configured with a **Static External IP** to ensure persistent connectivity for the mobile client.

---

## 🔗 Connection Details

* **API Base URL:** `http://35.224.85.222/`
* **Main Endpoint:** `http://35.224.85.222/api/tours` (Retrieve and manage travel data)

---

## 🛠️ Installation & Setup

### 1. For Users (Android)
* Locate the installation file: `app-debug.apk`.
* Install the file on any Android device.
* Ensure the device has an active internet connection to sync data with the Google Cloud server.

### 2. For Developers (Backend Management)
* Access the Google Cloud Instance via SSH.
* Navigate to the project directory:
    ```bash
    cd Tour-moment/tour-backend
    ```
* Deploy the services using Docker:
    ```bash
    sudo docker-compose up -d
    ```
* Verify service status:
    ```bash
    sudo docker ps
    ```

---

APK LINK: https://drive.google.com/drive/folders/1Y79Nb29QetVqiRlrOXo0kE4RSOB2y15z?dmr=1&ec=wgc-drive-%5Bmodule%5D-goto
---

### Pro-tip for your submission:
You can also include a **"Screenshots"** section in this README and drag-and-drop some images of your App and your Google Cloud dashboard to make the documentation look even more impressive to your professors!
