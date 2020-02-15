package lihua.dynamo.testkit

import cats.effect.{Async, Resource, Sync}
import lihua.dynamo.ScanamoManagement
import org.scanamo.LocalDynamoDB
import cats.implicits._
import cats.Parallel
import com.amazonaws.services.dynamodbv2.model.{
  ResourceNotFoundException,
  ScalarAttributeType
}

object LocalDynamo extends ScanamoManagement {
  def client[F[_]](implicit F: Sync[F]) =
    Resource.make {
      F.delay(LocalDynamoDB.client())
    } { client =>
      F.delay(client.shutdown())
    }

  def clientWithTables[F[_]: Parallel](
      tables: (String, Seq[(String, ScalarAttributeType)])*
    )(implicit F: Async[F]
    ) =
    client[F].flatTap { client =>
      Resource.make {
        tables.toList.parTraverse {
          case (tableName, keyAttributes) =>
            ensureTable[F](client, tableName, keyAttributes, 10L, 10L)
        }
      } { _ =>
        tables.toList.parTraverse { t =>
          F.delay(LocalDynamoDB.deleteTable(client)(t._1)).void.recover {
            case e: ResourceNotFoundException => ()
          }
        }.void
      }
    }
}
