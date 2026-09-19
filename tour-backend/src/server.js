const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });

const app = require("./app");
const connectDB = require("./config/db");

async function startServer() {
  await connectDB();

  if (!process.env.DEEPSEEK_API_KEY) {
    console.warn("[Server] DEEPSEEK_API_KEY is not set - POST /api/chat will fail until it is configured.");
  }

  const port = Number(process.env.PORT || 3000);
  return app.listen(port, "0.0.0.0", () => {
    const address = port === 0 ? "an ephemeral test port" : `port ${port}`;
    console.log(`[Server] Server running on ${address}`);
  });
}

if (require.main === module) {
  startServer().catch((error) => {
    console.error(`[Server] Failed to start server: ${error.message}`);
    process.exit(1);
  });
}

module.exports = { startServer };
