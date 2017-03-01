import java.io.{ InputStream, OutputStream }
import java.nio.charset.Charset
import java.time._

import com.amazonaws.services.lambda.runtime.{ Context, RequestStreamHandler }

import scala.language.implicitConversions
import scala.language.postfixOps

import dispatch.Http

import scala.concurrent._
import scala.concurrent.duration._

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
  implicit val ec: ectrace.WrappedExecutionContext = ectrace.WrappedExecutionContext(ExecutionContext.global)
  implicit def liftOption[T](value: T): Option[T] = Some(value)

  def links(doc: Document): List[String] =
    (doc >> elementList("#tab-agenda-list ul.list li")).map(element =>
      (element >> elementList("a"))(1) >> attr("href")("a"))

  def getDatalistFields(article: Element): Map[String, String] =
    (article >> elements(".datalist"))
      .flatMap(_.children.sliding(2, 2).flatMap {
        case Seq(k, v) => Some((k >> text("dt")) -> (v >> text("dd")))
        case Seq(_) => None
      })
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

  def fetchDocument(uri: String): Future[Document] = {
    val browser = JsoupBrowser()

    // JsoupBrowser.get expects UTF-8, vvvdeventer is windows codepage
    Http(dispatch.url(uri) OK dispatch.as.String.charset(Charset.forName("windows-1252"))).map {
      val doc = browser.parseString(_)
      doc
    }
  }

  def event(url: String): Future[Event] = {
    fetchDocument(url).map(doc => parseEvent(url, doc))
  }

  def fetchCalendar(): String = {
    val now = java.time.LocalDate.now()
    val urlPrefix =
      s"http://www.deventer.info/nl/agenda/jaarkalender?sub=30&f_agenda_start_date=01-${now.getMonth.ordinal + 1}-${now.getYear}&f_agenda_end_date=31-12-${now.getYear}&start="
    val futures: Seq[Future[List[Event]]] = Range(0, 5)
      .map(urlPrefix + _ + "0")
      .map(url => fetchDocument(url).flatMap(doc => Future.sequence(links(doc).map(event))))

    val results: List[Event] = Await.result(Future.sequence(futures), 120 seconds).flatten.toList


    // ec.dumpToFile("timeline.data")
    asIcal(Calendar(
      prodid = Prodid("-//raboof/vvv2ical//NONSGML v1.0//NL"),
      events = results
    ))
  }
}

class MainLambda extends Main {
  def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit = {
    context.getLogger().log("starting\n")
    val result = fetchCalendar()
    outputStream.write(result.getBytes("UTF-8"));
    outputStream.flush();
    context.getLogger().log("returning\n")
  }
}

object MainApp extends App with Main {
  print(fetchCalendar())
  dispatch.Http.shutdown()
}
