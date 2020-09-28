package flightapp;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
    // DB Connection
    private Connection conn;

    // Password hashing parameter constants
    private static final int HASH_STRENGTH = ...;
    private static final int KEY_LENGTH = ...;

    // Canned queries
    private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flightcapacity WHERE fid = ?";
    private PreparedStatement checkFlightCapacityStatement;

    // For check dangling
    private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
    private PreparedStatement tranCountStatement;

    // transactions
    private static final String BEGIN_TRANSACTION_SQL = "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; BEGIN TRANSACTION;";
    protected PreparedStatement beginTransactionStatement;

    private static final String COMMIT_SQL = "COMMIT TRANSACTION";
    protected PreparedStatement commitTransactionStatement;

    private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
    protected PreparedStatement rollbackTransactionStatement;

    // TODO: YOUR CODE HERE
    // Logged In User
    private String username; // customer username is unique

    // Record Itinerary
    public List<Itinerary> itArray;

    public Query() throws SQLException, IOException {
        this(null, null, null, null);
    }

    protected Query(String serverURL, String dbName, String adminName, String password)
            throws SQLException, IOException {
        conn = serverURL == null ? openConnectionFromDbConn()
                : openConnectionFromCredential(serverURL, dbName, adminName, password);

        prepareStatements();
    }

    /**
     * Return a connecion by using dbconn.properties file
     *
     * @throws SQLException
     * @throws IOException
     */
    public static Connection openConnectionFromDbConn() throws SQLException, IOException {
        // Connect to the database with the provided connection configuration
        Properties configProps = new Properties();
        configProps.load(new FileInputStream("...properties"));
        String serverURL = configProps.getProperty("...server_url");
        String dbName = configProps.getProperty("...database_name");
        String adminName = configProps.getProperty("...username");
        String password = configProps.getProperty("...password");
        return openConnectionFromCredential(serverURL, dbName, adminName, password);
    }

    /**
     * Return a connecion by using the provided parameter.
     *
     * @param serverURL example: example.database.widows.net
     * @param dbName    database name
     * @param adminName username to login server
     * @param password  password to login server
     *
     * @throws SQLException
     */
    protected static Connection openConnectionFromCredential(String serverURL, String dbName,
                                                             String adminName, String password) throws SQLException {
        String connectionUrl =
                String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
                        dbName, adminName, password);
        Connection conn = DriverManager.getConnection(connectionUrl);

        // By default, automatically commit after each statement
        conn.setAutoCommit(true);

        // By default, set the transaction isolation level to serializable
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

        return conn;
    }

    /**
     * Get underlying connection
     */
    public Connection getConnection() {
        return conn;
    }

    /**
     * Closes the application-to-database connection
     */
    public void closeConnection() throws SQLException {
        conn.close();
    }

    /**
     * Clear the data in any custom tables created.
     *
     * WARNING! Do not drop any tables and do not clear the flights table.
     */
    public void clearTables ()
    {
        try{
            conn.setAutoCommit(false);
            beginTransactionStatement.executeUpdate();

            String deleteTables = "DELETE FROM Reservations;\n" +
                    "DELETE FROM ID;\n" + "DELETE FROM Users;\n" +
                    "DELETE FROM Flightcapacity;\n";

            PreparedStatement ps = conn.prepareStatement(deleteTables);
            ps.clearParameters();
            ps.executeUpdate();

            // reset the next reservation ID to be 1.
            String updateIDQuery = "INSERT INTO ID VALUES(1);\n";
            ps = conn.prepareStatement(updateIDQuery);
            ps.clearParameters();
            ps.executeUpdate();

            // This update statement should be used theoretically.
            // However, it takes very long to complete. So an satisfactory alt may be used.
            String updateFlightsQuery =
                    "INSERT INTO Flightcapacity\n" +
                    "SELECT fid, capacity FROM Flights\n" +
                    "WHERE (origin_city = 'Seattle WA' AND dest_city IN ('Boston MA', 'Austin TX', 'Miami FL'))\n" +
                    "OR (origin_city IN ('Kahului HI', 'Boston MA') AND dest_city = 'Los Angeles CA')";
            ps = conn.prepareStatement(updateFlightsQuery);
            ps.clearParameters();
            ps.executeUpdate();

            ps.close();
            commitTransactionStatement.executeUpdate();
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
     * prepare all the SQL statements in this method.
     */
    private void prepareStatements() throws SQLException {
        checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
        tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
        // TODO: YOUR CODE HERE
        beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
        commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
        rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);
    }

    /**
     * Takes a user's username and password and attempts to log the user in.
     *
     * @param username user's username
     * @param password user's password
     *
     * @return If someone has already logged in, then return "User already logged in\n" For all other
     *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
     */
    public String transaction_login(String username, String password)
    {
        if (this.username != null) {
            return "User already logged in\n";
        } else {
            try {
                // conn.setAutoCommit(false);
                // beginTransactionStatement.executeUpdate();

                String loginQuery = "SELECT username FROM Users WHERE username = ? AND pw = ?";
                PreparedStatement ps = conn.prepareStatement(loginQuery);
                ps.setString(1, username);
                ps.setString(2, password);

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    this.username = rs.getString(1);  // only one col = username
                }
                rs.close();
                // commitTransactionStatement.executeUpdate();
                // conn.setAutoCommit(true);

                if (this.username == null) {
                    return "Login failed\n";
                }

                return "Logged in as " + this.username + "\n";
            } catch (SQLException e) {
                // e.printStackTrace();
                return "Login failed\n";
            }
        }
    }

    /**
     * Implement the create user function.
     *
     * @param username   new user's username. User names are unique the system.
     * @param password   new user's password.
     * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
     *                   otherwise).
     *
     * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
     */
    public String transaction_createCustomer(String username, String password, int initAmount) {
        if (initAmount < 0 || username.length() > 20 || password.length() > 20) {
            return "Failed to create user\n";
        }
        try {
            String loginQuery = "INSERT INTO Users VALUES(?, ?, ?);\n";
            PreparedStatement ps = conn.prepareStatement(loginQuery);

            ps.clearParameters();
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setInt(3, initAmount);
            ps.executeUpdate();

            // commitTransactionStatement.executeUpdate();
            // conn.setAutoCommit(true);
            //conn.rollback();
            //conn.setAutoCommit(true);
            ps.close();
            return "Created user " + username + "\n";

        } catch (SQLException e) {
//            e.printStackTrace();
            return "Failed to create user\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implement the search function.
     *
     * Searches for flights from the given origin city to the given destination city, on the given day
     * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
     * is searches for direct flights and flights with two "hops." Only searches for up to the number
     * of itineraries given by {@code numberOfItineraries}.
     *
     * The results are sorted based on total flight time.
     *
     * @param originCity
     * @param destinationCity
     * @param directFlight        if true, then only search for direct flights, otherwise include
     *                            indirect flights as well
     * @param dayOfMonth
     * @param numberOfItineraries number of itineraries to return
     *
     * @return If no itineraries were found, return "No flights match your selection\n". If an error
     *         occurs, then return "Failed to search\n".
     *
     *         Otherwise, the sorted itineraries printed in the following format:
     *
     *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
     *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
     *
     *         Each flight should be printed using the same format as in the {@code Flight} class.
     *         Itinerary numbers in each search should always start from 0 and increase by 1.
     *
     * @see Flight#toString()
     */
    public String transaction_search(String originCity, String destinationCity, boolean directFlight,
                                     int dayOfMonth, int numberOfItineraries) {
        try {
            // WARNING the below code is unsafe and only handles searches for direct flights
            // You can use the below code as a starting reference point or you can get rid
            // of it all and replace it with your own implementation.
            //
            // TODO: YOUR CODE HERE

            StringBuffer sb = new StringBuffer();
            itArray = new ArrayList<Itinerary>();

            try {
                int count = 0;
                // df for direct flights
                String dfQuery = "SELECT TOP (?) fid,day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price "
                        + "FROM Flights "
                        + "WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? AND canceled = 0 "
                        + "ORDER BY actual_time ASC, fid ASC";
                PreparedStatement ps = conn.prepareStatement(dfQuery);

                ps.clearParameters();
                ps.setInt(1, numberOfItineraries);
                ps.setString(2, originCity);
                ps.setString(3, destinationCity);
                ps.setInt(4, dayOfMonth);

                ResultSet df = ps.executeQuery();

                while (df.next()) {
                    int result_fid = df.getInt("fid");
                    //System.out.println(result_fid);
                    int result_dayOfMonth = df.getInt("day_of_month");
                    String result_carrierId = df.getString("carrier_id");
                    String result_flightNum = df.getString("flight_num");
                    String result_originCity = df.getString("origin_city");
                    String result_destCity = df.getString("dest_city");
                    int result_time = df.getInt("actual_time");
                    int result_capacity = df.getInt("capacity");
                    int result_price = df.getInt("price");

//                    sb.append("Itinerary ").append(count)
//                            .append(": 1 flight(s), ").append(result_time)
//                            .append(" minutes\n")
//                            .append("ID: ").append(result_fid)
//                            .append(" Day: ").append(result_dayOfMonth)
//                            .append(" Carrier: ").append(result_carrierId)
//                            .append(" Number: ").append(result_flightNum)
//                            .append(" Origin: ").append(result_originCity)
//                            .append(" Dest: ").append(result_destCity)
//                            .append(" Duration: ").append(result_time)
//                            .append(" Capacity: ").append(result_capacity)
//                            .append(" Price: ").append(result_price)
//                            .append("\n");

                    // set -1 to both fid2, cap2 since they are "null" in direct flights
                    itArray.add(new Itinerary(
                            count,
                            new Flight(
                                    result_fid, result_dayOfMonth, result_carrierId,
                                    result_flightNum, result_originCity, result_destCity,
                                    result_time, result_capacity, result_price
                            ), null, result_price, result_dayOfMonth, result_time
                    ));

                    count++;
                }
                df.close();

                boolean executeIndf = count < numberOfItineraries & !directFlight;
                if (executeIndf) {
                    int totalIndf = numberOfItineraries - count;
                    String indfQuery = "SELECT TOP (?) F1.fid,F1.day_of_month,F1.carrier_id,F1.flight_num,F1.origin_city,F1.dest_city,F1.actual_time,F1.capacity,F1.price,"
                            + "F2.fid,F2.carrier_id,F2.flight_num,F2.origin_city,F2.dest_city,F2.actual_time,F2.capacity,F2.price "
                            + "FROM Flights AS F1, Flights AS F2 "
                            + "WHERE F1.origin_city = ? AND F1.dest_city = F2.origin_city AND F2.dest_city = ? "
                            + "AND F1.day_of_month = F2.day_of_month AND F1.month_id = F2.month_id AND F1.day_of_month = ? "
                            + "AND F1.canceled = 0 AND F2.canceled = 0 "
                            + "ORDER BY (F1.actual_time + F2.actual_time) ASC, F1.fid ASC, F2.fid ASC";

                    ps = conn.prepareStatement(indfQuery);
                    ps.clearParameters();
                    ps.setInt(1, totalIndf);
                    ps.setString(2, originCity);
                    ps.setString(3, destinationCity);
                    ps.setInt(4, dayOfMonth);

                    ResultSet indf = ps.executeQuery();

                    while (indf.next()) {
                        int r_fid1 = indf.getInt(1);
                        int r_fid2 = indf.getInt(10);
                        int r_dayOfMonth = indf.getInt(2);
                        String r_carrier1 = indf.getString(3);
                        String r_carrier2 = indf.getString(11);
                        String r_flightNum1 = indf.getString(4);
                        String r_flightNum2 = indf.getString(12);
                        String r_org1 = indf.getString(5);
                        String r_org2 = indf.getString(13);
                        String r_dest1 = indf.getString(6);
                        String r_dest2 = indf.getString(14);
                        int r_time1 = indf.getInt(7);
                        int r_time2 = indf.getInt(15);
                        int r_cap1 = indf.getInt(8);
                        int r_cap2 = indf.getInt(16);
                        int r_price1 = indf.getInt(9);
                        int r_price2 = indf.getInt(17);

//                        sb.append("Itinerary ").append(count)
//                                .append(": 2 flight(s), ").append(r_time1 + r_time2)
//                                .append(" minutes\n")
//                                .append("ID: ").append(r_fid1)
//                                .append(" Day: ").append(r_dayOfMonth)
//                                .append(" Carrier: ").append(r_carrier1)
//                                .append(" Number: ").append(r_flightNum1)
//                                .append(" Origin: ").append(r_org1)
//                                .append(" Dest: ").append(r_dest1)
//                                .append(" Duration: ").append(r_time1)
//                                .append(" Capacity: ").append(r_cap1)
//                                .append(" Price: ").append(r_price1)
//                                .append("\n")
//                                .append("ID: ").append(r_fid2)
//                                .append(" Day: ").append(r_dayOfMonth)
//                                .append(" Carrier: ").append(r_carrier2)
//                                .append(" Number: ").append(r_flightNum2)
//                                .append(" Origin: ").append(r_org2)
//                                .append(" Dest: ").append(r_dest2)
//                                .append(" Duration: ").append(r_time2)
//                                .append(" Capacity: ").append(r_cap2)
//                                .append(" Price: ").append(r_price2)
//                                .append("\n");

                        itArray.add(new Itinerary(
                                count,
                                new Flight(
                                    r_fid1, r_dayOfMonth, r_carrier1,
                                    r_flightNum1, r_org1, r_dest1,
                                    r_time1, r_cap1, r_price1
                                ),
                                new Flight(
                                    r_fid2, r_dayOfMonth, r_carrier2,
                                    r_flightNum2, r_org2, r_dest2,
                                    r_time2, r_cap2, r_price2
                                ), r_price1 + r_price2,
                                r_dayOfMonth, r_time1 + r_time2
                        ));

                        count++;
                    }
                    indf.close();
                }
                Collections.sort(itArray);

                if (count == 0) {
                    sb.append("No flights match your selection\n");
                }
                for (int i = 0; i < itArray.size(); i++) {
                    Itinerary it = itArray.get(i);
                    sb.append(it.toString(i) + "\n");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            return sb.toString();
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implements the book itinerary function.
     *
     * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
     *                    the current session.
     *
     * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
     *         If the user is trying to book an itinerary with an invalid ID or without having done a
     *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
     *         a reservation on the same day as the one that they are trying to book now, then return
     *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
     *         failed\n".
     *
     *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
     *         where reservationId is a unique number in the reservation system that starts from 1 and
     *         increments by 1 each time a successful reservation is made by any user in the system.
     */
    public String transaction_book(int itineraryId) {
        if (this.username == null) {
            return "Cannot book reservations, not logged in\n";
        }
        int index = 0;
        boolean valid = false;
        if (itArray == null) {
            return "No such itinerary " + itineraryId + "\n";
        }
        try {
            while (itArray.get(index) != null) {
                if (itArray.get(index).iid == itineraryId) {
                    valid = true;
                    break;
                }
                index++;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return "No such itinerary " + itineraryId + "\n";
        }
        if (!valid) {
            return "No such itinerary " + itineraryId + "\n";
        }

        int retryCounter1 = 0;
        while(true) {
            try {
                conn.setAutoCommit(false);
                beginTransactionStatement.executeUpdate();

                Itinerary book = itArray.get(itineraryId);
                int fid1 = book.f1.fid;

                String sameDayQuery =
                        "SELECT day_of_month FROM Flights, Reservations\n" +
                        "WHERE username = ? AND fid = fid1";
                PreparedStatement ps = conn.prepareStatement(sameDayQuery);
                ps.clearParameters();
                ps.setString(1, this.username);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    int r_day = rs.getInt(1);
                    if (book.day == r_day) {
                        rollbackTransactionStatement.executeUpdate();
                        conn.setAutoCommit(true);
                        rs.close();
                        return "You cannot book two flights in the same day\n";
                    }
                }

                // check capacity
                int cap1 = 0;
                int cap2 = 0;


                ps = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
                ps.clearParameters();
                ps.setInt(1, book.f1.fid);
                rs = ps.executeQuery();

                while (rs.next()) {
                    cap1 = rs.getInt(1);
                    if (cap1 < 1) {
                        rollbackTransactionStatement.executeUpdate();
                        conn.setAutoCommit(true);
                        rs.close();
                        return "Booking failed\n";
                    }
                }

                int fid2 = -1;
                if (book.f2 != null) {
                    fid2 = book.f2.fid;
                    ps.clearParameters();
                    ps.setInt(1, book.f2.fid);
                    rs = ps.executeQuery();

                    while (rs.next()) {
                        cap2 = rs.getInt(1);
                        if (cap2 < 1) {
                            rollbackTransactionStatement.executeUpdate();
                            conn.setAutoCommit(true);
                            rs.close();
                            return "Booking failed\n";
                        }
                    }
                }

                // update Flightcapacity table (subtract 1 from capacity of each fid)
                String updateFlightsQuery = "UPDATE Flightcapacity SET capacity = ? WHERE fid = ?;\n";
                ps = conn.prepareStatement(updateFlightsQuery);
                ps.clearParameters();
                if (cap1 != 0) {
                    ps.setInt(1, cap1 - 1);
                    ps.setInt(2, fid1);
                    ps.executeUpdate();
                }


                if (fid2 != -1) {
                    ps.clearParameters();
                    if (cap2 != 0) {
                        ps.setInt(1, cap2 - 1);
                        ps.setInt(2, fid2);
                        ps.executeUpdate();
                    }
                }

                // update Reservation table (add rid and such)
                int retryCounter2 = 0;
                while (true) {
                    try {
                        String ridQuery = "SELECT max(rid) FROM ID;\n";
                        ps = conn.prepareStatement(ridQuery);
                        ps.clearParameters();
                        rs = ps.executeQuery();

                        int rid = 1;
                        if (rs.next()) {
                            rid = rs.getInt(1);  // only one value
                        }

                        String updateResQuery = "INSERT INTO Reservations VALUES(?, ?, ?, ?, ?, ?);\n";
                        ps = conn.prepareStatement(updateResQuery);
                        ps.clearParameters();
                        ps.setInt(1, rid);
                        ps.setInt(2, 0);  // paid value
                        ps.setInt(3, fid1);
                        ps.setInt(4, fid2);
                        ps.setString(5, this.username);
                        ps.setInt(6, book.price);
                        ps.executeUpdate();

                        String updateRidQuery = "UPDATE ID SET rid = ?";
                        ps = conn.prepareStatement(updateRidQuery);
                        ps.clearParameters();
                        ps.setInt(1, rid + 1);
                        ps.executeUpdate();

                        commitTransactionStatement.executeUpdate();
                        conn.setAutoCommit(true);
                        rs.close();
                        return "Booked flight(s), reservation ID: " + rid + "\n";
                    } catch (SQLException e) {
                        if (retryCounter2 < 2) {
                            continue;
                        } else {
                            e.printStackTrace();
                        }
                    } finally {
                        retryCounter2++;
                    }
                }
            } catch (SQLException e) {
                if (retryCounter1 < 2) {
                    try {
                        rollbackTransactionStatement.executeUpdate();
                        conn.setAutoCommit(true);
                        continue;
                    } catch (SQLException ee) {
                    }
                } else {
                    e.printStackTrace();
                    return "Booking failed\n";
                }
            } finally {
                retryCounter1++;
                checkDanglingTransaction();
            }
        }
    }

    /**
     * Implements the pay function.
     *
     * @param reservationId the reservation to pay for.
     *
     * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
     *         is not found / not under the logged in user's name, then return "Cannot find unpaid
     *         reservation [reservationId] under user: [username]\n" If the user does not have enough
     *         money in their account, then return "User has only [balance] in account but itinerary
     *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
     *         [reservationId]\n"
     *
     *         If successful, return "Paid reservation: [reservationId] remaining balance:
     *         [balance]\n" where [balance] is the remaining balance in the user's account.
     */
    public String transaction_pay(int reservationId) {

        if (this.username == null) {
            return "Cannot pay, not logged in\n";
        }

        try {
            // TODO: YOUR CODE HERE
            conn.setAutoCommit(false);
            beginTransactionStatement.executeUpdate();

            String checkResQuery = "SELECT rid FROM Reservations WHERE username = ? "
                    + "AND paid = 0;\n";

            PreparedStatement ps = conn.prepareStatement(checkResQuery);
            ps.clearParameters();
            ps.setString(1, this.username);
            ResultSet rs = ps.executeQuery();

            boolean foundUnpaid = false;

            while (rs.next()) {
                int r_rid = rs.getInt(1);
                if (r_rid == reservationId) {
                    foundUnpaid = true;
                    break;
                }
            }

            if (!foundUnpaid) {
                rollbackTransactionStatement.executeUpdate();
                conn.setAutoCommit(true);
                rs.close();
                return "Cannot find unpaid reservation " + reservationId + " under user: "
                        + this.username + "\n";
            }

            String costQuery = "SELECT price FROM Reservations WHERE rid = ?;\n";
            ps = conn.prepareStatement(costQuery);
            ps.clearParameters();
            ps.setInt(1, reservationId);
            rs = ps.executeQuery();
            rs.next();
            int cost = rs.getInt(1);

            String userQuery = "SELECT balance FROM Users WHERE username = ?;\n";
            ps = conn.prepareStatement(userQuery);
            ps.clearParameters();
            ps.setString(1, this.username);
            rs = ps.executeQuery();
            rs.next();
            int balance = rs.getInt(1);

            if (balance < cost) {
                rollbackTransactionStatement.executeUpdate();
                conn.setAutoCommit(true);
                rs.close();
                return "User has only " + balance + " in account but itinerary costs " + cost + "\n";
            }


            // update reservation table (unpaid -> paid)
            String updateResQuery = "UPDATE Reservations SET paid = 1 WHERE rid = ?;\n";
            ps = conn.prepareStatement(updateResQuery);
            ps.clearParameters();
            ps.setInt(1, reservationId);
            ps.executeUpdate();

            // update user table (balance decreases)
            int remaining_balance = balance - cost;
            String updateUserQuery = "UPDATE Users SET balance = ? WHERE username = ?;\n";
            ps = conn.prepareStatement(updateUserQuery);
            ps.clearParameters();
            ps.setInt(1, remaining_balance);
            ps.setString(2, this.username);
            ps.executeUpdate();

            commitTransactionStatement.executeUpdate();
            conn.setAutoCommit(true);
            rs.close();
            return "Paid reservation: " + reservationId + " remaining balance: " + remaining_balance + "\n";

        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to pay for reservation " + reservationId + "\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implements the reservations function.
     *
     * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
     *         the user has no reservations, then return "No reservations found\n" For all other
     *         errors, return "Failed to retrieve reservations\n"
     *
     *         Otherwise return the reservations in the following format:
     *
     *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
     *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
     *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
     *         reservation]\n ...
     *
     *         Each flight should be printed using the same format as in the {@code Flight} class.
     *
     * @see Flight#toString()
     */
    public String transaction_reservations() {

        if (this.username == null) {
            return "Cannot view reservations, not logged in\n";
        }

        try {
            // TODO: YOUR CODE HERE
            conn.setAutoCommit(false);
            beginTransactionStatement.executeUpdate();

            String resQuery = "SELECT rid, paid, fid1, fid2 FROM Reservations WHERE username = ?;\n";
            PreparedStatement ps = conn.prepareStatement(resQuery);
            ps.clearParameters();
            ps.setString(1, this.username);
            ResultSet rs = ps.executeQuery();

            StringBuffer sb = new StringBuffer();
            while (rs.next()) {
                int r_rid = rs.getInt(1);
                int r_paid = rs.getInt(2);
                int r_fid1 = rs.getInt(3);
                int r_fid2 = rs.getInt(4);
                String paid = (r_paid == 1) ? "true" : "false";

                sb.append("Reservation ").append(r_rid).append(" paid: ").append(paid).append(":\n");

                String flightQuery = "SELECT fid, day_of_month, carrier_id, flight_num, origin_city,"
                        + "dest_city, actual_time, capacity, price FROM Flights WHERE fid = ?;\n";
                ps = conn.prepareStatement(flightQuery);
                ps.clearParameters();
                ps.setInt(1, r_fid1);
                ResultSet frs = ps.executeQuery();  // flight resultset
                frs.next();

                int rf_fid = frs.getInt(1);  // rf for result [from] flight
                int rf_day_of_month = frs.getInt(2);
                String rf_cid = frs.getString(3);
                int rf_flight_num = frs.getInt(4);
                String rf_org_city = frs.getString(5);
                String rf_dest_city = frs.getString(6);
                int rf_actual_time = frs.getInt(7);
                int rf_capacity = frs.getInt(8);
                int rf_price = frs.getInt(9);

                sb.append("ID: ").append(rf_fid).append(" Day: ").append(rf_day_of_month)
                        .append(" Carrier: ").append(rf_cid).append(" Number: ").append(rf_flight_num)
                        .append(" Origin: ").append(rf_org_city).append(" Dest: ").append(rf_dest_city)
                        .append(" Duration: ").append(rf_actual_time).append(" Capacity: ").append(rf_capacity)
                        .append(" Price: ").append(rf_price).append("\n");

                if (r_fid2 != -1) {
                    ps = conn.prepareStatement(flightQuery);
                    ps.clearParameters();
                    ps.setInt(1, r_fid2);
                    frs = ps.executeQuery();
                    frs.next();

                    rf_fid = frs.getInt(1);
                    rf_day_of_month = frs.getInt(2);
                    rf_cid = frs.getString(3);
                    rf_flight_num = frs.getInt(4);
                    rf_org_city = frs.getString(5);
                    rf_dest_city = frs.getString(6);
                    rf_actual_time = frs.getInt(7);
                    rf_capacity = frs.getInt(8);
                    rf_price = frs.getInt(9);

                    sb.append("ID: ").append(rf_fid).append(" Day: ").append(rf_day_of_month)
                            .append(" Carrier: ").append(rf_cid).append(" Number: ").append(rf_flight_num)
                            .append(" Origin: ").append(rf_org_city).append(" Dest: ").append(rf_dest_city)
                            .append( "Duration: ").append(rf_actual_time).append(" Capacity: ").append(rf_actual_time)
                            .append(" Price: ").append(rf_price).append("\n");

                    frs.close();
                }
            }
            rs.close();
            if (sb.toString().equals("")) {
                rollbackTransactionStatement.executeUpdate();
                conn.setAutoCommit(true);
                return "No reservations found\n";
            } else {
                commitTransactionStatement.executeUpdate();
                conn.setAutoCommit(true);
                return sb.toString();
            }

        } catch (SQLException e) {
            return "Failed to retrieve reservations\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implements the cancel operation.
     *
     * @param reservationId the reservation ID to cancel
     *
     * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
     *         all other errors, return "Failed to cancel reservation [reservationId]\n"
     *
     *         If successful, return "Canceled reservation [reservationId]\n"
     *
     *         Even though a reservation has been canceled, its ID should not be reused by the system.
     */
    public String transaction_cancel(int reservationId) {

        if (this.username == null) {
            return "Cannot cancel reservations, not logged in\n";
        }

        try {
            // TODO: YOUR CODE HERE
            conn.setAutoCommit(false);
            beginTransactionStatement.executeUpdate();

            String resQuery = "SELECT rid, paid FROM Reservations WHERE username = ? AND rid = ?;\n";
            PreparedStatement ps = conn.prepareStatement(resQuery);
            ps.clearParameters();
            ps.setString(1, this.username);
            ps.setInt(2, reservationId);
            ResultSet rs = ps.executeQuery();

            String deleteResQuery = "DELETE FROM Reservations WHERE username = ? AND rid = ?;\n";
            ps = conn.prepareStatement(deleteResQuery);
            ps.clearParameters();
            if (rs.next()) {
                ps.setString(1, this.username);
                ps.setInt(2, reservationId);
                ps.executeUpdate();
                rs.close();
                commitTransactionStatement.executeUpdate();
                conn.setAutoCommit(true);
                return "Canceled reservation " + reservationId + "\n";
            } else {
                rollbackTransactionStatement.executeUpdate();
                conn.setAutoCommit(true);
                return "Failed to cancel reservation " + reservationId + "\n";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to cancel reservation " + reservationId + "\n";

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Example utility function that uses prepared statements
     */
    private int checkFlightCapacity(int fid) throws SQLException {
        checkFlightCapacityStatement.clearParameters();
        checkFlightCapacityStatement.setInt(1, fid);
        ResultSet results = checkFlightCapacityStatement.executeQuery();
        results.next();
        int capacity = results.getInt("capacity");
        results.close();

        return capacity;
    }

    /**
     * Throw IllegalStateException if transaction not completely complete, rollback.
     *
     */
    private void checkDanglingTransaction() {
        try {
            try (ResultSet rs = tranCountStatement.executeQuery()) {
                rs.next();
                int count = rs.getInt("tran_count");
                if (count > 0) {
                    throw new IllegalStateException(
                            "Transaction not fully commit/rollback. Number of transaction in process: " + count);
                }
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    private static boolean isDeadLock(SQLException ex) {
        return ex.getErrorCode() == 1205;
    }

    /**
     * A class to store flight information.
     */
    class Flight {
        public int fid;
        public int dayOfMonth;
        public String carrierId;
        public String flightNum;
        public String originCity;
        public String destCity;
        public int time;
        public int capacity;
        public int price;

        /**
         * Creates a flight with the given properties.
         */
        public Flight(
                int fid, int dayOfMonth, String carrierId, String flightNum,
                String originCity, String destCity, int time, int capacity, int price
        ) {
            this.fid = fid;
            this.dayOfMonth = dayOfMonth;
            this.carrierId = carrierId.trim();
            this.flightNum = flightNum.trim();
            this.originCity = originCity.trim();
            this.destCity = destCity.trim();
            this.time = time;
            this.capacity = capacity;
            this.price = price;
        }


        @Override
        public String toString() {
            return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
                    + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
                    + " Capacity: " + capacity + " Price: " + price;
        }
    }

    public class Itinerary implements Comparable<Itinerary> {
        public int iid;
        public Flight f1;
        public Flight f2;
        public int price;
        public int day;
        public int totalTime;
        public int numFlights = 1;

        public Itinerary(int iid, Flight f1, Flight f2, int price, int day, int totalTime) {
            this.iid = iid;
            this.f1 = f1;
            this.f2 = f2;
            this.price = price;
            this.day = day;
            this.totalTime = totalTime;
            if (this.f2 != null) {
                this.numFlights = 2;
            }
        }

        public String toString(int iid) {
            this.iid = iid;
            if (f2 != null) {
                return "Itinerary " + iid + ": " + this.numFlights + " flight(s), " + this.totalTime + " minutes\n" +
                        this.f1.toString() + "\n" + this.f2.toString();
            } else {
                return "Itinerary " + iid + ": " + this.numFlights + " flight(s), " + this.totalTime + " minutes\n" +
                        this.f1.toString();
            }
        }

        @Override
        public int compareTo(Itinerary anotherItinerary) {
            return totalTime - anotherItinerary.totalTime;
        }

    }
}
