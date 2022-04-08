---
title: Go ORMs
linkTitle: Go ORMs
description: Go ORMs for YSQL
headcontent: Go ORMs for YSQL
image: /images/section_icons/sample-data/s_s1-sampledata-3x.png
menu:
  preview:
    name: Go ORMs
    identifier: gorm-orm
    parent: go-drivers
    weight: 400
isTocNested: true
showAsideToc: true
---

<ul class="nav nav-tabs-alt nav-tabs-yb">

  <li >
    <a href="/preview/drivers-orms/go/gorm/" class="nav-link active">
      <i class="icon-postgres" aria-hidden="true"></i>
      GORM ORM
    </a>
  </li>

  <li >
    <a href="/preview/drivers-orms/go/pg/" class="nav-link">
      <i class="icon-postgres" aria-hidden="true"></i>
      PG ORM
    </a>
  </li>

</ul>

[GORM](https://gorm.io/) is the ORM library for Golang.

## CRUD operations with GORM

Learn how to establish a connection to YugabyteDB database and begin basic CRUD operations using the steps on the [Build an application](../../../quick-start/build-apps/go/ysql-gorm) page under the Quick start section.

The following sections break down the quick start example to demonstrate how to perform common tasks required for Go application development using GORM.

### Step 1: Import the driver package

Import the GORM packages by adding the following import statement in your Go code.

```go
import (
  "github.com/jinzhu/gorm"
  _ "github.com/jinzhu/gorm/dialects/postgres"
)
```

### Step 2: Connect to the YugabyteDB database

Go applications can connect to the YugabyteDB database using the `gorm.Open()` function.

Code snippet for connecting to YugabyteDB:

```go
conn := fmt.Sprintf("host= %s port = %d user = %s password = %s dbname = %s sslmode=disable", host, port, user, password, dbname)
var err error
db, err = gorm.Open("postgres", conn)
defer db.Close()
if err != nil {
  panic(err)
}
```

| Parameter | Description | Default |
| :---------- | :---------- | :------ |
| host  | Hostname of the YugabyteDB instance | localhost
| port |  Listen port for YSQL | 5433
| user | User connecting to the database | yugabyte
| password | Password for connecting to the database | yugabyte
| dbname | Database name | yugabyte

### Step 3: Create a table

Define a struct which maps to the table schema and use `AutoMigrate()` to create the table.

```go
type Employee struct {
  Id       int64  `gorm:"primary_key"`
  Name     string `gorm:"size:255"`
  Age      int64
  Language string `gorm:"size:255"`
}
 ...

// Create table
db.Debug().AutoMigrate(&Employee{})
```

Read more on designing [Database schemas and tables](../../../explore/ysql-language-features/databases-schemas-tables/).

### Step 4: Read and write data

To write data to YugabyteDB, use the `db.Create()` function.

```go
// Insert value
db.Create(&Employee{Id: 1, Name: "John", Age: 35, Language: "Golang-GORM"})
db.Create(&Employee{Id: 2, Name: "Smith", Age: 24, Language: "Golang-GORM"})
```

To query data from YugabyteDB tables, use the `db.Find()` function.

```go
// Display input data
var employees []Employee
db.Find(&employees)
for _, employee := range employees {
  fmt.Printf("Employee ID:%d\nName:%s\nAge:%d\nLanguage:%s\n", employee.Id, employee.Name, employee.Age, employee.Language)
  fmt.Printf("--------------------------------------------------------------\n")
}
```

## Next steps

- Explore [Scaling Go Applications](/preview/explore/linear-scalability) with YugabyteDB.
- Learn how to [develop Go applications with Yugabyte Cloud](/preview/yugabyte-cloud/cloud-quickstart/cloud-build-apps/cloud-ysql-go/).