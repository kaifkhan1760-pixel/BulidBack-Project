package com.buildback.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time database upgrade for MySQL, run at startup.
 *
 * Older versions of BuildBack created materials.category and materials.unit as
 * MySQL ENUM columns, e.g. enum('FITTINGS','PAINT','PLYWOOD','TILES'). An ENUM
 * column rejects any new value (like WATERPROOFING), and Hibernate's
 * ddl-auto=update never changes an existing column. So, if we still find an
 * ENUM there, we change it to VARCHAR. Existing rows keep their values; nothing
 * is deleted. Once the columns are VARCHAR this does nothing.
 */
@Component
public class MaterialColumnsUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MaterialColumnsUpgrade.class);

    private final JdbcTemplate jdbc;

    public MaterialColumnsUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if (!"MySQL".equalsIgnoreCase(database)) {
            return; // tests use H2, which creates the columns fresh as VARCHAR
        }
        widenIfEnum("category", 20);
        widenIfEnum("unit", 10);
    }

    private void widenIfEnum(String column, int length) {
        List<String> types = jdbc.queryForList("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'materials' AND COLUMN_NAME = ?""",
                String.class, column);
        if (types.size() == 1 && "enum".equalsIgnoreCase(types.get(0))) {
            // column and length are fixed values from this class, never user input
            jdbc.execute("ALTER TABLE materials MODIFY COLUMN " + column + " VARCHAR(" + length + ") NOT NULL");
            log.info("Changed materials.{} from ENUM to VARCHAR({}) so new categories/units can be stored",
                    column, length);
        }
    }
}
