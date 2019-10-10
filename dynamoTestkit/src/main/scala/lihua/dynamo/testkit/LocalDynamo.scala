package lihua.dynamo.testkit

import cats.effect.{Async, Resource, Sync}
import lihua.dynamo.ScanamoEntityDAO
import org.scanamo.LocalDynamoDB
import cats.implicits._

import cats.Parallel

object LocalDynamo {

  def client[F[_]](implicit F: Sync[F]) =
    Resource.make {
      F.delay(LocalDynamoDB.client())
    } { client =>
      F.delay(client.shutdown())
    }

  def clientWithTables[F[_]: Parallel](
      tables: String*
    )(implicit F: Async[F]
    ) =
    client[F].flatTap { client =>
      Resource.make {
        tables.toList.parTraverse(
          ScanamoEntityDAO.ensureTable[F](client, _)
        )
      } { _ =>
        tables.toList.parTraverse { t =>
          F.delay(LocalDynamoDB.deleteTable(client)(t))
        }.void
      }
    }

}
