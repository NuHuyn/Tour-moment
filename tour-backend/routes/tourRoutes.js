const express = require("express");
const Tour = require("../models/Tour");
const Schedule = require("../models/Schedule");

const router = express.Router();


// CREATE TOUR
router.post("/", async (req,res)=>{

  try{

    const {
      tour_name,
      description,
      location,
      price,
      image_url,
      created_by
    } = req.body;

    const tour = new Tour({
      tour_name,
      description,
      location,
      price,
      image_url,
      created_by
    });

    await tour.save();

    res.json(tour);

  }catch(err){
    res.status(500).json(err);
  }

});


router.get("/", async (req, res) => {

  try {

    const tours = await Tour.aggregate([
      {
        $lookup: {
          from: "schedules",          // collection schedule
          localField: "_id",
          foreignField: "tour_id",
          as: "schedule"
        }
      },
      {
        $unwind: {
          path: "$schedule",
          preserveNullAndEmptyArrays: true
        }
      },
      {
        $addFields: {
          start_date: "$schedule.start_date",
          end_date: "$schedule.end_date"
        }
      }
    ]);

    res.json(tours);

  } catch (err) {
    res.status(500).json(err);
  }

});


module.exports = router;