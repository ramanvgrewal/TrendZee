import { MongoClient } from "mongodb";

const uri = process.env.MONGODB_URI;
const dbName = process.env.MONGODB_DB_NAME ?? process.env.MONGODB_DATABASE;

let clientPromise: Promise<unknown> | undefined;
let loggedConfig = false;

export async function getMongoDb() {
  if (!uri) {
    throw new Error("Missing MONGODB_URI environment variable.");
  }

  if (!clientPromise) {
    const client = new MongoClient(uri);
    clientPromise = client.connect();
  }

  const client = (await clientPromise) as { db: (name?: string) => unknown };
  if (!loggedConfig) {
    console.info(`[TrendZY] MongoDB database: ${dbName || "(default from URI)"}`);
    loggedConfig = true;
  }
  return client.db(dbName);
}
