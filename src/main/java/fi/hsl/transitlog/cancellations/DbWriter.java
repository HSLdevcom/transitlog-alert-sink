package fi.hsl.transitlog.cancellations;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;


public class DbWriter {
    private static final Logger log = LoggerFactory.getLogger(DbWriter.class);

    Connection connection;

    private DbWriter(Connection conn) {
        connection = conn;
    }

    public static DbWriter newInstance(Config config) throws Exception {
        final String connectionString = config.getString("db.connectionString");
        final String user = config.getString("db.username");
        final String password = config.getString("db.password");

        log.info("Connecting to the database with connection string " + connectionString);
        Connection conn = DriverManager.getConnection(connectionString, user, password);
        //conn.setAutoCommit(false); // we're doing batch inserts so no auto commit
        log.info("Connection success");
        return new DbWriter(conn);
    }

    private String createInsertStatement() {
        return new StringBuffer()
                .append("INSERT INTO TRIP (")
                .append("start_date, ")
                .append("route_id, ")
                .append("direction_id, ")
                .append("start_time, ")
                .append("json_schema_version, ")
                .append("trip_data, ")
                .append("ext_id_dvj")
                .append(") VALUES (")
                .append("?, ?, ?, ?, ?, ?, ?")
                .append(");")
                .toString();
    }

    public void insert(Trip trip) throws Exception {

        long startTime = System.currentTimeMillis();
        String queryString = createInsertStatement();
        try (PreparedStatement statement = connection.prepareStatement(queryString)) {
            int index = 1;

            setNullable(index++, trip.startDate, Types.DATE, statement);
            setNullable(index++, trip.routeId, Types.VARCHAR, statement);
            setNullable(index++, trip.directionId, Types.INTEGER, statement);
            setNullable(index++, trip.startTime30HourClock, Types.VARCHAR, statement);
            statement.setInt(index++, Trip.JSON_SCHEMA_VERSION);

            String jsonAsString = trip.tripData.toString(); //TODO  null check
            setNullable(index++, jsonAsString, Types.VARCHAR, statement);

            setNullable(index++, trip.dvjId.orElseGet(null), Types.VARCHAR, statement);
            statement.execute();
            connection.commit();
        }
        catch (Exception e) {
            log.error("Failed to insert to database: ", e);
            connection.rollback();
            throw e;
        }
        finally {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Total insert time: {} ms", elapsed);
        }
    }

    private void setNullable(int index, Object value, int jdbcType, PreparedStatement statement) throws SQLException {
        if (value == null) {
            statement.setNull(index, jdbcType);
        }
        else {
            //This is just awful but Postgres driver does not support setObject(value, type);
            //Leaving null values not set is also not an option.
            switch (jdbcType) {
                case Types.BOOLEAN: statement.setBoolean(index, (Boolean)value);
                    break;
                case Types.INTEGER: statement.setInt(index, (Integer) value);
                    break;
                case Types.BIGINT: statement.setLong(index, (Long)value);
                    break;
                case Types.DOUBLE: statement.setDouble(index, (Double) value);
                    break;
                case Types.DATE: statement.setDate(index, (Date)value);
                    break;
                case Types.TIME: statement.setTime(index, (Time)value);
                    break;
                case Types.VARCHAR: statement.setString(index, (String)value); //Not sure if this is correct, field in schema is TEXT
                    break;
                default: log.error("Invalid jdbc type, bug in the app! {}", jdbcType);
                    break;
            }
        }
    }

    public void close() {
        log.info("Closing DB Connection");
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            log.error("Failed to close DB Connection", e);
        }

    }
}