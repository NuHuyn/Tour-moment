const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");
const path = require('path');

const app = express();

app.use(cors());
app.use(express.json());

const authRoutes = require("./routes/authRoutes");
const tourRoutes = require("./routes/tourRoutes");


app.use("/api/auth", authRoutes);
app.use("/api/tours", tourRoutes);
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

mongoose.connect(
"mongodb+srv://huynhtranainu_db_user:tourapp234@cluster0.a281lm4.mongodb.net/newtour?retryWrites=true&w=majority"
)
.then(()=> console.log("MongoDB connected"))
.catch(err => console.log(err));

app.get("/", (req,res)=>{
    res.send("API running");
});

app.listen(3000, ()=>{
    console.log("Server running on port 3000");
});