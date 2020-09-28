# Flight_Booking_Service_System<br /> 
Complete a backend flight booking system<br /> 
Objectives:<br /> 
*To develop a database application under concurrent access.<br /> 
*To interface with a relational database from a Java application via JDBC.<br /> 

#### Implement create, login, and search<br /> 
Implement the create, login and search commands

#### Implement book, pay, reservations, cancel, and add transactions!<br /> 
Implement the book, pay , reservations and cancel commands.<br /> 
While implementing & trying out these commands, you'll notice that there are problems when multiple users try to use your service concurrently.<br /> 
To resolve this challenge, you will need to implement transactions that ensure concurrent commands do not conflict.<br /> 
#### Transaction management
You must use SQL transactions to guarantee ACID properties: we have set the isolation level for your Connection, and you need to define<br /> 
begin-transaction and end-transaction statements and insert them in appropriate places in Query.java.<br /> 
In particular, you must ensure that the following constraints are always satisfied, even if multiple instances of your application talk to the database at the same<br />  time:
C1: Each flight should have a maximum capacity that must not be exceeded. Each flight’s capacity is stored in the Flights table as in HW3, and you should <br /> have records as to how many seats remain on each flight based on the reservations.<br /> 
C2: A customer may have at most one reservation on any given day, but they can be on more than 1 flight on the same day. (i.e., a customer can have <br /> one reservation on a given day that includes two flights, because the reservation is for a one-hop itinerary).<br /> 
You must use transactions correctly such that race conditions introduced by concurrent execution cannot lead to an inconsistent state of the database.<br /> 
For example, multiple customers may try to book the same flight at the same time. Your properly designed transactions should prevent that.<br /> 
Design transactions correctly. Avoid including user interaction inside a SQL transaction: that is, don't begin a transaction then wait for the user to decide <br /> what she wants to do (why?).<br /> 
The rule of thumb is that transactions need to be as short as possible, but not shorter.<br /> 
Your executeQuery call will throw a SQLException when an error occurs (e.g., multiple customers try to book the same flight concurrently).<br /> 
Make sure you handle the SQLException appropriately.<br /> 
For instance, if a seat is still available, the booking should eventually go through (even though you might need to retry due to SQLExceptions being thrown).<br /> 
If no seat is available, the booking should be rolled back, etc.<br /> 





