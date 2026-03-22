const mongoose = require("mongoose");

const scheduleSchema = new mongoose.Schema({

  tour_id:{
    type:mongoose.Schema.Types.ObjectId,
    ref:"Tour"
  },

  start_date:{
    type:Date
  },

  end_date:{
    type:Date
  },

  available_slots:{
    type:Number
  }

});

module.exports = mongoose.model("Schedule",scheduleSchema);