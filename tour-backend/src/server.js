const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });

const app = require("./app");
const connectDB = require("./config/db");

const PORT = process.env.PORT || 3000;

async function startServer() {
  try {
    // Kết nối CSDL MongoDB
    await connectDB();

    // Chatbot (DeepSeek) is optional at boot - don't block server startup on it, just warn
    // loudly so a missing key shows up in the logs instead of as a silent 500 on first chat.
    if (!process.env.DEEPSEEK_API_KEY) {
      console.warn("[Server] DEEPSEEK_API_KEY is not set - POST /api/chat will fail until it is configured.");
    }

    // Lắng nghe cổng HTTP
    app.listen(PORT, () => {
      console.log(`[Server] Server running on port ${PORT}`);
    });
  } catch (error) {
    console.error(`[Server] Failed to start server: ${error.message}`);
    process.exit(1);
  }
}

startServer();
