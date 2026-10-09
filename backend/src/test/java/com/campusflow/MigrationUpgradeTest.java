package com.campusflow;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class MigrationUpgradeTest {
    @TempDir Path baseline;
    @Test void publishedV6UpgradesWithoutChangingExistingBusinessData() throws Exception {
        for(int version=1;version<=6;version++) {
            final int v=version;
            try(var paths=Files.list(Path.of("../database/migrations"))) {
                var source=paths.filter(p->p.getFileName().toString().startsWith("V"+v+"__")).findFirst().orElseThrow();
                if(v==6) source=Path.of("src/test/resources/migration-baseline/V6__space_coordinate_system.sql");
                Files.copy(source,baseline.resolve(source.getFileName()));
            }
        }
        String url="jdbc:h2:mem:upgrade_compatibility;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("filesystem:"+baseline).load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            statement.executeUpdate("UPDATE study_space SET description='保留原有资料' WHERE id=1");
        }
        assertDoesNotThrow(()->Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate());
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            var result=statement.executeQuery("SELECT description,coordinate_system FROM study_space WHERE id=1");result.next();
            assertEquals("保留原有资料",result.getString(1));assertEquals("GCJ02",result.getString(2));
            statement.executeQuery("SELECT * FROM hardware_record");statement.executeQuery("SELECT * FROM hardware_device");statement.executeQuery("SELECT * FROM hardware_discovery");
        }
    }
}
