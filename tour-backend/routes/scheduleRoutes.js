const express = require("express");
const Schedule = require("../models/Schedule");

const router = express.Router();


// CREATE SCHEDULE
router.post("/", async (req,res)=>{

  try{

    const {
      tour_id,
      start_date,
      end_date,
      available_slots
    } = req.body;

    const schedule = new Schedule({
      tour_id,
      start_date,
      end_date,
      available_slots
    });

    await schedule.save();

    res.json(schedule);

  }catch(err){
    res.status(500).json(err);
  }

});


// GET SCHEDULE BY TOUR
router.get("/tour/:tourId", async (req,res)=>{

  const schedules = await Schedule.find({
    tour_id:req.params.tourId
  });

  res.json(schedules);

});

module.exports = router;