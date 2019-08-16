package fi.hsl.transitlog.alerts;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import fi.hsl.common.transitdata.proto.InternalMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;


public class DbWriter {
    private static final Logger log = LoggerFactory.getLogger(DbWriter.class);
    private static Calendar calendar;

    Connection connection;

    private DbWriter(Connection conn) {
        connection = conn;
    }

    public static DbWriter newInstance(Config config, final String connectionString) throws Exception {
        final String timeZone = config.getString("db.timezone");
        calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone));

        log.info("Connecting to the database");
        Connection conn = DriverManager.getConnection(connectionString);
        conn.setAutoCommit(true);
        log.info("Connection success");
        return new DbWriter(conn);
    }

    private String createInsertStatement() {
        return new StringBuffer()
                .append("INSERT INTO alert (")
                .append("route_id, ")
                .append("stop_id, ")
                .append("affects_all_routes, ")
                .append("affects_all_stops, ")
                .append("valid_from, ")
                .append("valid_to, ")
                .append("last_modified, ")
                .append("data, ")
                .append("ext_id_bulletin")
                .append(") VALUES (")
                .append("?, ?, ?, ?, ?, ?, ?, ?::JSON, ?")
                .append(") ON CONFLICT DO NOTHING;") // Let's just ignore duplicates
                .toString();
    }

    public void insert(final InternalMessages.Bulletin bulletin) throws Exception {
        final long now = System.currentTimeMillis();
        String queryString = createInsertStatement();
        try {
            for (final InternalMessages.Bulletin.AffectedEntity entity : bulletin.getAffectedRoutesList()) {
                insert(bulletin, queryString, AffectedEntityType.ROUTE, entity);
            }
            for (final InternalMessages.Bulletin.AffectedEntity entity : bulletin.getAffectedStopsList()) {
                insert(bulletin, queryString, AffectedEntityType.STOP, entity);
            }
            if ((bulletin.getAffectedRoutesCount() == 0 && bulletin.getAffectedStopsCount() == 0) &&
                    ((bulletin.hasAffectsAllRoutes() && bulletin.getAffectsAllRoutes()) || (bulletin.hasAffectsAllStops() && bulletin.getAffectsAllStops()))) {
                insert(bulletin, queryString, null, null);
            }
        } finally {
            final long elapsed = System.currentTimeMillis() - now;
            log.info("Total insert time: {} ms", elapsed);
        }
    }

    private enum AffectedEntityType {
        ROUTE,
        STOP,
    }

    private void insert(final InternalMessages.Bulletin bulletin, final String queryString, final AffectedEntityType type, final InternalMessages.Bulletin.AffectedEntity entity) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(queryString)) {
            int index = 1;

            if (type == null) {
                setNullable(index++, null, Types.VARCHAR, statement);
                setNullable(index++, null, Types.VARCHAR, statement);
            } else {
                switch (type) {
                    case ROUTE:
                        setNullable(index++, entity.getEntityId(), Types.VARCHAR, statement);
                        setNullable(index++, null, Types.VARCHAR, statement);
                        break;
                    case STOP:
                        setNullable(index++, null, Types.VARCHAR, statement);
                        setNullable(index++, entity.getEntityId(), Types.VARCHAR, statement);
                        break;
                    default:
                        setNullable(index++, null, Types.VARCHAR, statement);
                        setNullable(index++, null, Types.VARCHAR, statement);
                        break;
                }
            }
            if (bulletin.hasAffectsAllRoutes() && bulletin.getAffectsAllRoutes()) {
                setNullable(index++, bulletin.getAffectsAllRoutes(), Types.BOOLEAN, statement);
            } else {
                setNullable(index++, false, Types.BOOLEAN, statement);
            }
            if (bulletin.hasAffectsAllStops() && bulletin.getAffectsAllStops()) {
                setNullable(index++, bulletin.getAffectsAllStops(), Types.BOOLEAN, statement);
            } else {
                setNullable(index++, false, Types.BOOLEAN, statement);
            }
            setNullable(index++, Timestamp.from(Instant.ofEpochMilli(bulletin.getValidFromUtcMs())), Types.TIMESTAMP_WITH_TIMEZONE, statement);
            setNullable(index++, Timestamp.from(Instant.ofEpochMilli(bulletin.getValidToUtcMs())), Types.TIMESTAMP_WITH_TIMEZONE, statement);
            setNullable(index++, Timestamp.from(Instant.ofEpochMilli(bulletin.getLastModifiedUtcMs())), Types.TIMESTAMP_WITH_TIMEZONE, statement);

            // set json data
            final ObjectNode json = JsonNodeFactory.instance.objectNode();
            json.put("category", bulletin.getCategory().toString());
            json.put("impact", bulletin.getImpact().toString());
            json.put("priority", bulletin.getPriority().toString());

            final ArrayNode titles = json.putArray("titles");
            for (final InternalMessages.Bulletin.Translation translation : bulletin.getTitlesList()) {
                final ObjectNode title = JsonNodeFactory.instance.objectNode();
                title.put("text", translation.getText());
                title.put("language", translation.getLanguage());
                titles.add(title);
            }

            final ArrayNode descriptions = json.putArray("descriptions");
            for (final InternalMessages.Bulletin.Translation translation : bulletin.getDescriptionsList()) {
                final ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.put("text", translation.getText());
                description.put("language", translation.getLanguage());
                descriptions.add(description);
            }

            final ArrayNode urls = json.putArray("urls");
            for (final InternalMessages.Bulletin.Translation translation : bulletin.getUrlsList()) {
                final ObjectNode url = JsonNodeFactory.instance.objectNode();
                url.put("text", translation.getText());
                url.put("language", translation.getLanguage());
                urls.add(url);
            }

            setNullable(index++, json.toString(), Types.VARCHAR, statement);

            setNullable(index++, bulletin.getBulletinId(), Types.VARCHAR, statement);
            statement.execute();
        }
        catch (Exception e) {
            log.error("Failed to insert service alert to database: ", e);
            throw e;
        }
    }

    private static long toUtcEpochMs(LocalDateTime localTimestamp, String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        return localTimestamp.atZone(zone).toInstant().toEpochMilli();
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
                case Types.TIMESTAMP_WITH_TIMEZONE: statement.setTimestamp(index, (Timestamp)value, calendar);
                    break;
                case Types.VARCHAR: statement.setString(index, (String)value); //Not sure if this is correct, field in schema is TEXT
                    break;
                case Types.JAVA_OBJECT: statement.setObject(index, value);
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
