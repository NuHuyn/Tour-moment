const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });

const app = require("./app");
const connectDB = require("./config/db");

const PORT = process.env.PORT || 3000;

async function startServer() {
  try {
    // Kết nối CSDL MongoDB
    await connectDB();

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
