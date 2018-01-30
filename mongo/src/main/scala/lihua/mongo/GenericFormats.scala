package lihua.mongo

import io.estatico.newtype.BaseNewType
import play.api.libs.json.{Format, OFormat}
import julienrf.json.derived.{DerivedOWrites, DerivedReads, NameAdapter}
import shapeless.Lazy

object PlayJsonFormats extends PlayJsonFormats0 {

  implicit def newTypeFormat[Base, Repr, Tag](implicit ev: Format[Repr]): Format[BaseNewType.Aux[Base, Tag, Repr]] =
    ev.asInstanceOf[Format[BaseNewType.Aux[Base, Tag, Repr]]]

}

abstract class PlayJsonFormats0 {
  implicit def genericFormat[A](implicit derivedReads: Lazy[DerivedReads[A]],
                                derivedOWrites: Lazy[DerivedOWrites[A]]): OFormat[A] =
    julienrf.json.derived.oformat(NameAdapter.snakeCase)
}

