/*
 * Copyright (c) 2016 SnappyData, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package io.snappydata.benchmark

import java.sql.Statement

import org.apache.spark.SparkContext
import org.apache.spark.sql.snappy._
import org.apache.spark.sql.{SQLContext, SaveMode, SnappyContext}


object TPCHColumnPartitionedTable  {

  def createPartTable_Memsql(stmt:Statement): Unit = {
    stmt.execute("CREATE TABLE PART  ( " +
        "P_PARTKEY     INTEGER NOT NULL,"+
        "P_NAME        VARCHAR(55) NOT NULL,"+
        "P_MFGR        VARCHAR(25) NOT NULL,"+
        "P_BRAND       VARCHAR(10) NOT NULL,"+
        "P_TYPE        VARCHAR(25) NOT NULL,"+
        "P_SIZE        INTEGER NOT NULL,"+
        "P_CONTAINER   VARCHAR(10) NOT NULL,"+
        "P_RETAILPRICE DECIMAL(15,2) NOT NULL,"+
        "P_COMMENT     VARCHAR(23) NOT NULL," +
        "KEY (P_PARTKEY) USING CLUSTERED COLUMNSTORE,"+
        "SHARD KEY (P_PARTKEY))"
    )
    println("Created Table PART")
  }


  def createPartSuppTable_Memsql(stmt:Statement): Unit = {
    stmt.execute("CREATE TABLE PARTSUPP ( " +
        "PS_PARTKEY     INTEGER NOT NULL," +
        "PS_SUPPKEY     INTEGER NOT NULL," +
        "PS_AVAILQTY    INTEGER NOT NULL," +
        "PS_SUPPLYCOST  DECIMAL(15,2)  NOT NULL," +
        "PS_COMMENT     VARCHAR(199) NOT NULL," +
        "KEY (PS_PARTKEY) USING CLUSTERED COLUMNSTORE,"+
	      "SHARD KEY (PS_PARTKEY))"
    )
    println("Created Table PARTSUPP")
  }

  def createCustomerTable_Memsql(stmt:Statement): Unit = {
    stmt.execute("CREATE TABLE CUSTOMER ( " +
        "C_CUSTKEY     INTEGER NOT NULL," +
        "C_NAME        VARCHAR(25) NOT NULL," +
        "C_ADDRESS     VARCHAR(40) NOT NULL," +
        "C_NATIONKEY   INTEGER NOT NULL," +
        "C_PHONE       VARCHAR(15) NOT NULL," +
        "C_ACCTBAL     DECIMAL(15,2)   NOT NULL," +
        "C_MKTSEGMENT  VARCHAR(10) NOT NULL," +
        "C_COMMENT     VARCHAR(117) NOT NULL," +
        "KEY (C_CUSTKEY) USING CLUSTERED COLUMNSTORE)"
    )
    println("Created Table CUSTOMER")
  }

  def createOrderTable_Memsql(stmt: Statement): Unit = {

    stmt.execute("CREATE TABLE ORDERS  ( " +
        "O_ORDERKEY       INTEGER NOT NULL," +
        "O_CUSTKEY        INTEGER NOT NULL," +
        "O_ORDERSTATUS    CHAR(1) NOT NULL," +
        "O_TOTALPRICE     DECIMAL(15,2) NOT NULL," +
        "O_ORDERDATE      DATE NOT NULL," +
        "O_ORDERPRIORITY  CHAR(15) NOT NULL," +
        "O_CLERK          CHAR(15) NOT NULL," +
        "O_SHIPPRIORITY   INTEGER NOT NULL," +
        "O_COMMENT        VARCHAR(79) NOT NULL," +
        "KEY (O_CUSTKEY) USING CLUSTERED COLUMNSTORE,"+
        "SHARD KEY(O_ORDERKEY))"
    )
    println("Created Table ORDERS")
  }

  def createAndPopulateOrderTable(props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets: String): Unit = {
    val sc = sqlContext.sparkContext
    val orderData = sc.textFile(s"$path/orders.tbl")
    val orderReadings = orderData.map(s => s.split('|')).map(s => TPCHTableSchema.parseOrderRow(s))
    val orderDF = sqlContext.createDataFrame(orderReadings)
    val newSchema = TPCHTableSchema.newOrderSchema(orderDF.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "o_orderkey"),("BUCKETS"-> buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("customer", ifExists = true)
      snappyContext.dropTable("part", ifExists = true)
      snappyContext.dropTable("LINEITEM", ifExists = true)
      snappyContext.dropTable("ORDERS", ifExists = true)

      snappyContext.createTable("ORDERS", "column", newSchema, p1)
      orderDF.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("ORDERS")
      println("Created Table ORDERS")
    } else {
      orderDF.registerTempTable("ORDERS")
      sqlContext.cacheTable("ORDERS")
      val cnts = sqlContext.sql("select count(*) from ORDERS").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }

  def createAndPopulateOrder_CustTable(props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets: String): Unit = {
    val sc = sqlContext.sparkContext
    val orderData = sc.textFile(s"$path/orders.tbl")
    val orderReadings = orderData.map(s => s.split('|')).map(s => TPCHTableSchema.parseOrderRow(s))
    val orderDF = sqlContext.createDataFrame(orderReadings)
    val newSchema = TPCHTableSchema.newOrderSchema(orderDF.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "o_custkey"),("BUCKETS"-> buckets), ("COLOCATE_WITH"->"CUSTOMER"))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("ORDERS_CUST", ifExists = true)
      snappyContext.createTable("ORDERS_CUST", "column", newSchema, p1)
      orderDF.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("ORDERS_CUST")
      println("Created Table ORDERS_CUST")
    }
  }

  def createLineItemTable_Memsql(stmt: Statement): Unit = {
    stmt.execute("CREATE TABLE LINEITEM ( L_ORDERKEY    INTEGER NOT NULL,"+
        "L_PARTKEY     INTEGER NOT NULL,"+
        "L_SUPPKEY     INTEGER NOT NULL,"+
        "L_LINENUMBER  INTEGER NOT NULL,"+
        "L_QUANTITY    DECIMAL(15,2) NOT NULL,"+
        "L_EXTENDEDPRICE  DECIMAL(15,2) NOT NULL,"+
        "L_DISCOUNT    DECIMAL(15,2) NOT NULL,"+
        "L_TAX         DECIMAL(15,2) NOT NULL,"+
        "L_RETURNFLAG  CHAR(1) NOT NULL,"+
        "L_LINESTATUS  CHAR(1) NOT NULL,"+
        "L_SHIPDATE    DATE NOT NULL,"+
        "L_COMMITDATE  DATE NOT NULL,"+
        "L_RECEIPTDATE DATE NOT NULL,"+
        "L_SHIPINSTRUCT CHAR(25) NOT NULL,"+
        "L_SHIPMODE     CHAR(10) NOT NULL,"+
        "L_COMMENT      VARCHAR(44) NOT NULL,"+
        "KEY (L_PARTKEY) USING CLUSTERED COLUMNSTORE,"+
        "SHARD KEY (L_ORDERKEY)) "
    )

    println("Created Table LINEITEM")
  }

  def createAndPopulateLineItemTable(props: Map[String, String], sqlContext: SQLContext, path:String, isSnappy:Boolean, buckets: String): Unit = {
    val sc = sqlContext.sparkContext
    val lineItemData = sc.textFile(s"$path/lineitem.tbl")
    val lineItemReadings = lineItemData.map(s => s.split('|')).map(s => TPCHTableSchema.parseLineItemRow(s))
    val lineOrderDF = sqlContext.createDataFrame(lineItemReadings)
    val newSchema = TPCHTableSchema.newLineItemSchema(lineOrderDF.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "l_orderkey"),("COLOCATE_WITH"->"ORDERS"),("BUCKETS"->buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("LINEITEM", ifExists = true)
      snappyContext.createTable("LINEITEM", "column", newSchema, p1)
      lineOrderDF.write.insertInto("LINEITEM")
      println("Created Table LINEITEM")
    } else {
      lineOrderDF.registerTempTable("LINEITEM")
      sqlContext.cacheTable("LINEITEM")
      var cnts = sqlContext.sql("select count(*) from LINEITEM").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }

  def createAndPopulateLineItem_partTable(props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets: String): Unit = {
    val sc = sqlContext.sparkContext
    val lineItemData = sc.textFile(s"$path/lineitem.tbl")
    val lineItemReadings = lineItemData.map(s => s.split('|')).map(s => TPCHTableSchema.parseLineItemRow(s))
    val lineOrderDF = sqlContext.createDataFrame(lineItemReadings)
    val newSchema = TPCHTableSchema.newLineItemSchema(lineOrderDF.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "l_partkey"),("COLOCATE_WITH"->"PART"),("BUCKETS"->buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("LINEITEM_PART", ifExists = true)
      snappyContext.createTable("LINEITEM_PART", "column", newSchema, p1)
      lineOrderDF.write.insertInto("LINEITEM_PART")
      println("Created Table LINEITEM_PART")
    }
  }
  def createPopulateCustomerTable(usingOptionString: String, props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets:String): Unit = {
    val sc = sqlContext.sparkContext
    val customerData = sc.textFile(s"$path/customer.tbl")
    val customerReadings = customerData.map(s => s.split('|')).map(s => TPCHTableSchema.parseCustomerRow(s))
    val customerDF = sqlContext.createDataFrame(customerReadings)
    val newSchema = TPCHTableSchema.newCustomerSchema(customerDF.schema)

    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "c_custkey"),("BUCKETS"->buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("ORDERS_CUST", ifExists = true)
      snappyContext.dropTable("CUSTOMER", ifExists = true)
      snappyContext.createTable("CUSTOMER", "column", newSchema, p1)
      customerDF.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("CUSTOMER")
      println("Created Table CUSTOMER")
    } else {
      customerDF.registerTempTable("CUSTOMER")
      sqlContext.cacheTable("CUSTOMER")
      val cnts = sqlContext.sql("select count(*) from CUSTOMER").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }


  def createPopulatePartTable(usingOptionString: String, props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets:String): Unit = {
    val sc = sqlContext.sparkContext
    val partData = sc.textFile(s"$path/part.tbl")
    val partReadings = partData.map(s => s.split('|')).map(s => TPCHTableSchema.parsePartRow(s))
    val partDF = sqlContext.createDataFrame(partReadings)
    val newSchema = TPCHTableSchema.newPartSchema(partDF.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "p_partkey"),("BUCKETS"->buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("LINEITEM_PART", ifExists = true)
      snappyContext.dropTable("PART", ifExists = true)
      snappyContext.createTable("PART", "column", newSchema, p1)
      partDF.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("PART")
      println("Created Table PART")
    } else {
      partDF.registerTempTable("PART")
      sqlContext.cacheTable("PART")
      val cnts = sqlContext.sql("select count(*) from PART").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }

  def createPopulatePartSuppTable(usingOptionString: String, props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets:String): Unit = {
    val sc = sqlContext.sparkContext
    val partSuppData = sc.textFile(s"$path/partsupp.tbl")
    val partSuppReadings = partSuppData.map(s => s.split('|')).map(s => TPCHTableSchema.parsePartSuppRow(s))
    val partSuppDF = sqlContext.createDataFrame(partSuppReadings)
    val newSchema = TPCHTableSchema.newPartSuppSchema(partSuppDF.schema)

    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "ps_partkey"),("BUCKETS"->buckets),("COLOCATE_WITH"->"PART"))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("PARTSUPP", ifExists = true)
      snappyContext.createTable("PARTSUPP", "column", newSchema, p1)
      partSuppDF.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("PARTSUPP")
      println("Created Table PARTSUPP")
    } else {
      partSuppDF.registerTempTable("PARTSUPP")
      sqlContext.cacheTable("PARTSUPP")
      val cnts = sqlContext.sql("select count(*) from PARTSUPP").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }


  def createAndPopulateOrderSampledTable(props: Map[String, String],
      sc: SparkContext, path: String): Unit = {
    val snappyContext = SnappyContext(sc)
    val orderDF = snappyContext.table("ORDERS")
    val orderSampled = orderDF.stratifiedSample(Map(
      "qcs" -> "O_ORDERDATE", // O_SHIPPRIORITY
      "fraction" -> 0.03,
      "strataReservoirSize" -> 50))
    orderSampled.registerTempTable("ORDERS_SAMPLED")
    snappyContext.cacheTable("orders_sampled")
    println("Created Sampled Table ORDERS_SAMPLED " + snappyContext.sql(
      "select count(*) as sample_count from orders_sampled").collectAsList())
  }

  def createAndPopulateLineItemSampledTable(props: Map[String, String],
      sc: SparkContext, path: String): Unit = {
    val snappyContext = SnappyContext(sc)
    val lineOrderDF = snappyContext.table("LINEITEM")
    val lineOrderSampled = lineOrderDF.stratifiedSample(Map(
      "qcs" -> "L_SHIPDATE", // L_RETURNFLAG
      "fraction" -> 0.03,
      "strataReservoirSize" -> 50))
    println(" Logic relation while creation " + lineOrderSampled.logicalPlan.output)
    lineOrderSampled.registerTempTable("LINEITEM_SAMPLED")
    snappyContext.cacheTable("lineitem_sampled")
    println("Created Sampled Table LINEITEM_SAMPLED " + snappyContext.sql(
      "select count(*) as sample_count from lineitem_sampled").collectAsList())
  }

  def createAndPopulateNationTable(props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets: String): Unit = {
    val sc = sqlContext.sparkContext
    val orderData = sc.textFile(s"$path/nation.tbl")
    val nationreadings = orderData.map(s => s.split('|')).map(s => TPCHTableSchema.parseNationRow(s))
    val nationdf = sqlContext.createDataFrame(nationreadings)
    val newSchema = TPCHTableSchema.newNationSchema(nationdf.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "N_NATIONKEY"),("BUCKETS"-> buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("NATION", ifExists = true)
      snappyContext.createTable("NATION", "column", newSchema, p1)
      nationdf.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("NATION")
      println("Created Table NATION")
    } else {
      nationdf.registerTempTable("NATION")
      sqlContext.cacheTable("NATION")
      val cnts = sqlContext.sql("select count(*) from NATION").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }

  def createAndPopulateRegionTable(props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets: String): Unit = {
    //val snappyContext = SnappyContext.getOrCreate(sc)
    val sc = sqlContext.sparkContext
    val orderData = sc.textFile(s"$path/region.tbl")
    val regionreadings = orderData.map(s => s.split('|')).map(s => TPCHTableSchema.parseRegionRow(s))
    val regiondf = sqlContext.createDataFrame(regionreadings)
    val newSchema = TPCHTableSchema.newRegionSchema(regiondf.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "R_REGIONKEY"),("BUCKETS"-> buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("REGION", ifExists = true)
      snappyContext.createTable("REGION", "column", newSchema, p1)
      regiondf.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("REGION")
      println("Created Table REGION")
    } else {
      regiondf.registerTempTable("REGION")
      sqlContext.cacheTable("REGION")
      val cnts = sqlContext.sql("select count(*) from REGION").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }

  def createAndPopulateSupplierTable(props: Map[String, String], sqlContext: SQLContext, path: String, isSnappy: Boolean, buckets: String): Unit = {
    //val snappyContext = SnappyContext.getOrCreate(sc)
    val sc = sqlContext.sparkContext
    val orderData = sc.textFile(s"$path/supplier.tbl")
    val suppreadings = orderData.map(s => s.split('|')).map(s => TPCHTableSchema.parseSupplierRow(s))
    val suppdf = sqlContext.createDataFrame(suppreadings)
    val newSchema = TPCHTableSchema.newSupplierSchema(suppdf.schema)
    if (isSnappy) {
      val p1 = Map(("PARTITION_BY"-> "S_SUPPKEY"),("BUCKETS"-> buckets))

      val snappyContext = sqlContext.asInstanceOf[SnappyContext]
      snappyContext.dropTable("SUPPLIER", ifExists = true)
      snappyContext.createTable("SUPPLIER", "column", newSchema, p1)
      suppdf.write.format("column").mode(SaveMode.Append).options(p1).saveAsTable("SUPPLIER")
      println("Created Table SUPPLIER")
    } else {
      suppdf.registerTempTable("SUPPLIER")
      sqlContext.cacheTable("SUPPLIER")
      val cnts = sqlContext.sql("select count(*) from SUPPLIER").collect()
      for (s <- cnts) {
        var output = s.toString()
        println(output)
      }
    }
  }


}
