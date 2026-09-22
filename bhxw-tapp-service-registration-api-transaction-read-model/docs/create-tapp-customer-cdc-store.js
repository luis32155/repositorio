// Run with:
// mongosh "mongodb://localhost:27017" --file docs/create-tapp-customer-cdc-store.js

const targetDb = db.getSiblingDB("tapp_customer_cdc_store");
const collectionName = "customer_profiles";

const customerProfilesValidator = {
  $jsonSchema: {
    bsonType: "object",
    additionalProperties: false,
    required: [
      "_id",
      "basic_information",
      "identification",
      "individual",
      "institution",
      "contact_methods",
      "event_control",
      "created_at",
      "updated_at"
    ],
    properties: {
      _id: {
        bsonType: "objectId",
        description: "MongoDB internal ObjectId."
      },
      basic_information: {
        bsonType: "object",
        additionalProperties: false,
        required: [
          "customer_key",
          "customer_type",
          "profile_type",
          "full_name",
          "bt_account_number"
        ],
        properties: {
          customer_key: {
            bsonType: "string",
            minLength: 1,
            maxLength: 100
          },
          customer_type: {
            enum: ["PHYSICAL_PERSON", "LEGAL_PERSON"]
          },
          profile_type: {
            enum: ["CUS"]
          },
          full_name: {
            bsonType: "string",
            minLength: 1,
            maxLength: 100
          },
          bt_account_number: {
            bsonType: ["int", "long"],
            minimum: 0,
            maximum: 999999999
          }
        }
      },
      identification: {
        bsonType: "object",
        additionalProperties: false,
        required: ["country", "identification_type", "identification_no"],
        properties: {
          country: {
            bsonType: ["int", "long"],
            minimum: 0,
            maximum: 999
          },
          identification_type: {
            bsonType: ["int", "long"],
            minimum: 0,
            maximum: 99
          },
          identification_no: {
            bsonType: "string",
            minLength: 1,
            maxLength: 12
          }
        }
      },
      individual: {
        bsonType: ["object", "null"],
        additionalProperties: false,
        required: [
          "first_given_name",
          "first_last_name",
          "birth_date",
          "marital_status",
          "gender"
        ],
        properties: {
          first_given_name: {
            bsonType: "string",
            minLength: 1,
            maxLength: 25
          },
          second_given_name: {
            bsonType: ["string", "null"],
            maxLength: 25
          },
          first_last_name: {
            bsonType: "string",
            minLength: 1,
            maxLength: 30
          },
          second_last_name: {
            bsonType: ["string", "null"],
            maxLength: 30
          },
          birth_date: {
            bsonType: "string",
            pattern: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$"
          },
          marital_status: {
            bsonType: "string",
            minLength: 1,
            maxLength: 1
          },
          gender: {
            bsonType: "string",
            minLength: 1,
            maxLength: 1
          }
        }
      },
      institution: {
        bsonType: ["object", "null"],
        additionalProperties: false,
        required: ["legal_name", "incorporation_date"],
        properties: {
          legal_name: {
            bsonType: "string",
            minLength: 1,
            maxLength: 70
          },
          incorporation_date: {
            bsonType: "string",
            pattern: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$"
          }
        }
      },
      contact_methods: {
        bsonType: "object",
        additionalProperties: false,
        required: ["phone", "email"],
        properties: {
          phone: {
            bsonType: "array",
            items: {
              bsonType: "object",
              additionalProperties: false,
              required: ["value", "status"],
              properties: {
                value: {
                  bsonType: "string",
                  pattern: "^\\+?[0-9]{7,15}$"
                },
                status: {
                  enum: ["0", "1"]
                }
              }
            }
          },
          email: {
            bsonType: "array",
            items: {
              bsonType: "object",
              additionalProperties: false,
              required: ["value", "status"],
              properties: {
                value: {
                  bsonType: "string",
                  minLength: 3,
                  maxLength: 70
                },
                status: {
                  enum: ["0", "1"]
                }
              }
            }
          }
        }
      },
      event_control: {
        bsonType: "object",
        additionalProperties: false,
        required: [
          "event_id",
          "event_type",
          "event_version",
          "occurred_at",
          "source",
          "correlation_id",
          "trace_id"
        ],
        properties: {
          event_id: {
            bsonType: "string",
            minLength: 1,
            maxLength: 100
          },
          event_type: {
            enum: [
              "CUSTOMER_SNAPSHOT",
              "CUSTOMER_CREATED",
              "CUSTOMER_UPDATED",
              "CUSTOMER_DELETED"
            ]
          },
          event_version: {
            bsonType: ["int", "long"],
            minimum: 1
          },
          occurred_at: {
            bsonType: "date"
          },
          source: {
            enum: ["AS400"]
          },
          correlation_id: {
            bsonType: "string",
            minLength: 1,
            maxLength: 100
          },
          trace_id: {
            bsonType: "string",
            minLength: 1,
            maxLength: 100
          }
        }
      },
      created_at: {
        bsonType: "date"
      },
      updated_at: {
        bsonType: "date"
      }
    },
    oneOf: [
      {
        properties: {
          basic_information: {
            properties: {
              customer_type: {
                enum: ["PHYSICAL_PERSON"]
              }
            }
          },
          individual: {
            bsonType: "object"
          },
          institution: {
            bsonType: "null"
          }
        }
      },
      {
        properties: {
          basic_information: {
            properties: {
              customer_type: {
                enum: ["LEGAL_PERSON"]
              }
            }
          },
          individual: {
            bsonType: "null"
          },
          institution: {
            bsonType: "object"
          }
        }
      }
    ]
  }
};

if (targetDb.getCollectionNames().includes(collectionName)) {
  targetDb.runCommand({
    collMod: collectionName,
    validator: customerProfilesValidator,
    validationLevel: "strict",
    validationAction: "error"
  });
} else {
  targetDb.createCollection(collectionName, {
    validator: customerProfilesValidator,
    validationLevel: "strict",
    validationAction: "error"
  });
}

const customerProfiles = targetDb.getCollection(collectionName);

// Remove only legacy dummy records created by earlier versions of this script.
// MongoDB does not allow changing an existing document's _id in place.
customerProfiles.deleteMany({
  _id: {
    $in: [
      UUID("8f2a91c0-4c61-4f65-9a54-1d00f00a0001"),
      UUID("9a3b02d1-54f2-4f6b-b70e-2e00f00a0002"),
      ObjectId("66d9a0000000000000000001"),
      ObjectId("66d9a0000000000000000002")
    ]
  }
});

customerProfiles.createIndex(
  { "basic_information.customer_key": 1 },
  { unique: true, name: "uk_customer_key" }
);

customerProfiles.createIndex(
  {
    "identification.country": 1,
    "identification.identification_type": 1,
    "identification.identification_no": 1
  },
  {
    unique: true,
    name: "uk_customer_identification"
  }
);

customerProfiles.createIndex(
  { "basic_information.bt_account_number": 1 },
  { name: "idx_bt_account_number" }
);

customerProfiles.createIndex(
  { "contact_methods.phone.value": 1 },
  { name: "idx_phone_value" }
);

customerProfiles.createIndex(
  { "contact_methods.email.value": 1 },
  { name: "idx_email_value" }
);

customerProfiles.createIndex(
  { "event_control.event_id": 1 },
  { unique: true, name: "uk_last_event_id" }
);

const physicalCustomer = {
  basic_information: {
    customer_key: "8f2a91c0-4c61-4f65-9a54-1d00f00a0001",
    customer_type: "PHYSICAL_PERSON",
    profile_type: "CUS",
    full_name: "JANE MARIE DOE SMITH",
    bt_account_number: NumberLong(100000001)
  },
  identification: {
    country: 604,
    identification_type: 1,
    identification_no: "00000001"
  },
  individual: {
    first_given_name: "Jane",
    second_given_name: "Marie",
    first_last_name: "Doe",
    second_last_name: "Smith",
    birth_date: "1990-01-15",
    marital_status: "S",
    gender: "F"
  },
  institution: null,
  contact_methods: {
    phone: [
      {
        value: "900000001",
        status: "1"
      }
    ],
    email: [
      {
        value: "physical.customer@example.com",
        status: "1"
      }
    ]
  },
  event_control: {
    event_id: "EVT-DUMMY-PHYSICAL-001",
    event_type: "CUSTOMER_CREATED",
    event_version: NumberLong(1),
    occurred_at: ISODate("2026-09-09T10:00:00Z"),
    source: "AS400",
    correlation_id: "CORR-DUMMY-PHYSICAL-001",
    trace_id: "TRACE-DUMMY-PHYSICAL-001"
  },
  created_at: ISODate("2026-09-09T10:00:01Z"),
  updated_at: ISODate("2026-09-09T10:00:01Z")
};

const legalCustomer = {
  basic_information: {
    customer_key: "9a3b02d1-54f2-4f6b-b70e-2e00f00a0002",
    customer_type: "LEGAL_PERSON",
    profile_type: "CUS",
    full_name: "EXAMPLE COMPANY S.A.C.",
    bt_account_number: NumberLong(100000002)
  },
  identification: {
    country: 604,
    identification_type: 6,
    identification_no: "20000000000"
  },
  individual: null,
  institution: {
    legal_name: "Example Company S.A.C.",
    incorporation_date: "2015-04-20"
  },
  contact_methods: {
    phone: [
      {
        value: "900000002",
        status: "1"
      }
    ],
    email: [
      {
        value: "legal.customer@example.com",
        status: "1"
      }
    ]
  },
  event_control: {
    event_id: "EVT-DUMMY-LEGAL-001",
    event_type: "CUSTOMER_CREATED",
    event_version: NumberLong(1),
    occurred_at: ISODate("2026-09-09T10:05:00Z"),
    source: "AS400",
    correlation_id: "CORR-DUMMY-LEGAL-001",
    trace_id: "TRACE-DUMMY-LEGAL-001"
  },
  created_at: ISODate("2026-09-09T10:05:01Z"),
  updated_at: ISODate("2026-09-09T10:05:01Z")
};

customerProfiles.replaceOne(
  {
    "basic_information.customer_key":
      physicalCustomer.basic_information.customer_key
  },
  physicalCustomer,
  { upsert: true }
);

customerProfiles.replaceOne(
  {
    "basic_information.customer_key":
      legalCustomer.basic_information.customer_key
  },
  legalCustomer,
  { upsert: true }
);

print(`Database: ${targetDb.getName()}`);
print(`Collection: ${collectionName}`);
print(`Documents: ${customerProfiles.countDocuments({})}`);
printjson(
  customerProfiles
    .find(
      {},
      {
        _id: 1,
        "basic_information.customer_type": 1,
        "identification.identification_no": 1
      }
    )
    .toArray()
);
