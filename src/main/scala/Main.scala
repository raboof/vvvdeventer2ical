import java.io.{ InputStream, OutputStream }
import java.time._

import scala.language.implicitConversions
import scala.language.postfixOps

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import icalendar._
import icalendar.Properties._
import icalendar.CalendarProperties._
import icalendar.ValueTypes._
import icalendar.ical.Writer._

import net.ruippeixotog.scalascraper.model._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.HtmlExtractor

import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._

trait Main {
  val browser = JsoupBrowser()

  implicit def liftOption[T](value: T): Option[T] = Some(value)

  def links(doc: Document): List[String] =
    (doc >> elementList("#tab-agenda-list ul.list li")).map(element =>
      (element >> elementList("a"))(1) >> attr("href")("a"))

  def getDatalistFields(article: Element): Map[String, String] =
    (article >> elements(".datalist"))
      .flatMap(_.children.sliding(2, 2).map { case Seq(k, v) => ((k >> text("dt")) -> (v >> text("dd"))) })
      .toMap

  def parseEvent(url: String, doc: Document): Event = {
    val id = url.replaceAll("[^\\d]", "").toInt
    val article = doc >> element("article")
    val data = getDatalistFields(article)
    val datePattern = "(\\d+)-(\\d+)-(\\d+).*".r
    val date = data("Datum:") match {
      case datePattern(d, m, y) => s"$y-$m-$d"
    }
    val starttime = "\\d+:\\d+".r.findFirstIn(data("Geopend:")).getOrElse("00:00")

    Event(
      uid = Uid(s"vvvdeventer2ical-$id"),
      dtstart =
        ZonedDateTime.parse(s"${date}T${starttime}+02:00[Europe/Amsterdam]").withZoneSameInstant(ZoneOffset.UTC),
      summary = Summary(Text.fromString(article >> text("h1"))),
      description = Description(Text.fromString(article >> text("p"))),
      categories = List(Categories(ListType(data("Categorie:")))),
      url = Url(url)
    )
  }

  def fetchDocument(url: String): Future[Document] = Future { browser.get(url) }

  def event(url: String): Future[Event] = {
    fetchDocument(url).map(doc => parseEvent(url, doc))
  }

  def fetchCalendar(): String = {
    val urlPrefix =
      "http://www.deventer.info/nl/agenda/jaarkalender?sub=30&f_agenda_start_date=01-01-2016&f_agenda_end_date=31-12-2016&start="
    val futures: Seq[Future[List[Event]]] = Range(0, 5)
      .map(urlPrefix + _ + "0")
      .map(url => fetchDocument(url).flatMap(doc => Future.sequence(links(doc).map(event))))

    val results: List[Event] = Await.result(Future.sequence(futures), 20 seconds).flatten.toList
    asIcal(Calendar(
      prodid = Prodid("-//raboof/vvv2ical//NONSGML v1.0//NL"),
      events = results
    ))
  }
}

class MainLambda extends Main {
  def vvvdeventer2ical(input: InputStream, output: OutputStream): Unit = {
    output.write(fetchCalendar().getBytes("UTF-8"))
    output.flush()
  }
}

object MainApp extends App with Main {
  print(fetchCalendar())
}
