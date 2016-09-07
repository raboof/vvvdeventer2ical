import Main._

import icalendar.ical.Writer._

import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._

import org.scalatest._

class Test extends WordSpec with Matchers {
  val browser = JsoupBrowser()

  "The HTML scraping algorithm" should {
    "correctly find links in a yearly overview page" in {
      val doc = browser.parseResource("/jaarkalender.html")
      val links = Main.links(doc)
      links.size should be(10)
    }

    "correctly convert a details page to an event" in {
      val doc = browser.parseResource("/details.html", "Windows-1252")
      // println(asIcal(Main.parseEvent(72742, doc)))
    }
  }
}
