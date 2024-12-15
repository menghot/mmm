package com.mzl.flinkcdc.sql;

import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

public class Pg2HiveIceberg {

    public static void main(String[] args)  {

        Configuration config = new Configuration();

        // Can config in flink cluster
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

        //Catalog config if io-impl using org.apache.iceberg.aws.s3.S3FileIO
        //s3.endpoint, s3.access-key-id, s3.secret-access-key
        String createCatalog = "CREATE CATALOG hive_catalog WITH (\n" +
                "  'type'='iceberg',\n" +
                "  'catalog-type'='hive',\n" +
//                "  'uri'='thrift://192.168.80.241:9083',\n" +
//                "  'warehouse'='s3://hive/warehouse',\n" +

//                "  'io-impl'='org.apache.iceberg.aws.s3.S3FileIO',\n" +
//                "  's3.endpoint'='http://192.168.80.241:9000',\n" +
//                "  's3.access-key-id'='w2Xaege1JZSCpop1Dqd9',\n" +
//                "  's3.secret-access-key'='yWvk6CGD9FofHE78prTeDn3w3vrqgTtGStz2TnNq',\n" +
                "  'hive-conf-dir'='./hive-conf'\n," +
                "  'property-version'='1'" +
                ")\n";

        tEnv.executeSql(createCatalog);

        //Source table config
        tEnv.executeSql("CREATE DATABASE IF NOT EXISTS test");
        tEnv.executeSql("CREATE TABLE test.aaa (\n" +
                "    id                           STRING,\n" +
                "    show_profit_id               STRING,\n" +
                "    PRIMARY KEY (id) NOT ENFORCED\n" +
                ") WITH (\n" +
                "  'connector' = 'postgres-cdc',\n" +
                "  'hostname' = '192.168.80.241',\n" +
                "  'port' = '5432',\n" +
                "  'username' = 'postgres',\n" +
                "  'schema-name' = 'public',\n" +
                "  'slot.name' = 'aaa',\n" +
                "  'password' = 'thinker',\n" +
                "  'database-name' = 'hivemetastore',\n" +
                "  'decoding.plugin.name' = 'pgoutput',\n" +
//                "  'scan.incremental.snapshot.enabled' = 'true',\n" +
//                "  'scan.startup.mode' = 'initial',\n" +
                "  'table-name' = 'aaa'" +
                ")");


        // Execute streaming sql
        tEnv.executeSql("INSERT INTO hive_catalog.ods.test_cdc2 (id, profit)\n" +
                "select f.id,f.show_profit_id from test.aaa f");
    }
}
