# Templates

## Example Request

`conversation`

Not clear which templates to use, greet or ask for more info

---

`ride_request_preview`

Header: **Confirm the Ride Info**

Here is what we understood for your trip:

From: {{pickup_location}}
To: {{dropoff_location}}
When: {{pickup_time}}

If everything looks good, click "Confirm" to confirm your ride request. If you need to make any changes, send us a message with the updated details.

Button: **Confirm**

---

`ride_request`

Header: **Ride Request Received!**

Hi {{customer_name}}, we’ve successfully received your ride request for {{pickup_location}}.

We are currently matching you with the nearest available driver. Hang tight! We’ll send you the driver and vehicle details in just a moment. ⏱️

---

`driver_notification`

Header: **New Pickup Request!**

Pickup Location: {{pickup_location}}
Pickup Time: {{pickup_time}}
Destination: {{dropoff_location}}

Click "Accept" to claim this ride.

Footer: **First come, first served!**

---

`ride_confirmed`

Hello {{customer_name}},

We are confirming that {{driver_name}} will be your driver for your scheduled pickup.

Trip Details:

Date & Time: {{pickup_time}}
Pickup Location: {{pickup_location}}

Vehicle: {{vehicle_model}} - {{license_plate}}

Your driver will arrive promptly at the scheduled time. If you need to make any changes or contact your driver directly, please call {{driver_phone_number}}.

Thank you for riding with us!

---

`ride_cancelled`

Hi {{customer_name}},
We regret to inform you that your ride scheduled for {{pickup_location}} at {{pickup_time}} has been cancelled.
We apologize for any inconvenience this may cause.

Button: **Reschedule**
