package com.mzl.finkcdc.sql;

import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

public class SimplePostgresqlCDCSQL {

    public static void main(String[] args) throws Exception {

        Configuration config = new Configuration();
        config.set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem");
        config.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, "file:///tmp/flink/checkpoints");

        //Stream model
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);

        // Configure advanced checkpointing settings
        env.setParallelism(1);
        env.enableCheckpointing(5000);
        env.getCheckpointConfig().setCheckpointTimeout(10000); // Timeout after 10 seconds
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(1000); // Minimum interval between checkpoints
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1); // Allow only one checkpoint at a time
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        // Retain checkpoints after job cancellation
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
        );


        final StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        //Catalog config
        String createCatalog = "CREATE CATALOG hive_catalog WITH (\n" +
                "  'type'='iceberg',\n" +
                "  'catalog-type'='hive',\n" +
                "  'uri'='thrift://192.168.80.241:9083',\n" +
                "  'clients'='5',\n" +
                "  'property-version'='1',\n" +
                "  'warehouse'='s3a://hive/warehouse',\n" +
                "  'hive-conf-dir'='./hive-conf'\n" +
                ")\n";

        tEnv.executeSql(createCatalog);

        //Source table config
        tEnv.executeSql("CREATE DATABASE IF NOT EXISTS test");
        tEnv.executeSql("CREATE TABLE test.bbb (\n" +
                "    id                           STRING,\n" +
                "    show_profit_id               STRING,\n" +
                "    PRIMARY KEY (id) NOT ENFORCED\n" +
                ") WITH (\n" +
                "  'connector' = 'postgres-cdc',\n" +
                "  'hostname' = '192.168.80.241',\n" +
                "  'port' = '5432',\n" +
                "  'username' = 'postgres',\n" +
                "  'schema-name' = 'public',\n" +
                "  'slot.name' = 'bbb',\n" +
                "  'password' = 'thinker',\n" +
                "  'database-name' = 'hivemetastore',\n" +
                "  'decoding.plugin.name' = 'pgoutput',\n" +
//                "  'scan.incremental.snapshot.enabled' = 'true',\n" +
//                "  'scan.startup.mode' = 'initial',\n" +
                "  'table-name' = 'bbb'" +
                ")");

        System.out.println("------------");
        tEnv.executeSql("select count(*) from hive_catalog.ods.test_cdc2").print();
        System.out.println("------------");

        // Execute streaming sql
        tEnv.executeSql("INSERT INTO hive_catalog.ods.test_cdc2 (id, profit)\n" +
                "select f.id,f.show_profit_id from test.bbb f");
    }
}
