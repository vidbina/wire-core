package test.controllers

import java.util.UUID
import models._
import org.specs2.mutable._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._

class TransactionSpec extends Specification {
  
  "/transactions" should {

    "render the result as json upon request" in {
      running(FakeApplication()) {
        val headers = FakeHeaders(Seq("ACCEPT" -> Seq("application/json")))
        val request = FakeRequest(GET, "/transactions", headers, "")
        val page = route(request).get
        status(page) must equalTo(OK)
        contentType(page) must beSome.which(_ == "application/json")
      }
    }

    "render the index page" in {
      running(FakeApplication()) {
        val page = route(FakeRequest(GET, "/transactions")).get
        status(page) must equalTo(OK)
        contentType(page) must beSome.which(_ == "text/html")
      }
    }

    "send 404 on requesting details with non-existent id" in {
      running(FakeApplication()) {
        // html
        val uuid = (new UUID(0, 0)).toString
        val request = FakeRequest(GET, "/transactions/" + uuid)
        status(route(request).get) must equalTo(NOT_FOUND)

        val page = route(request).get
        contentType(page) must beSome.which(_ == "text/html")
      }
    }

    "send 404 on requesting json details with non-existing id" in {
      running(FakeApplication()) {
        // json
        val uuid = (new UUID(0, 0)).toString
        val headers = FakeHeaders(Seq("ACCEPT" -> Seq("application/json")))
        val request = FakeRequest(GET, "/transactions/" + uuid, headers, "")

        val page = route(request).get
        status(page) must equalTo(NOT_FOUND)
        contentType(page) must beSome.which(_ == "application/json")
      }
    }

    "returns the resource" in {
      running(FakeApplication()) {
        val t = Transaction.create(
          40000,
          Receiver("Daisy Buchanan", "686278912", "US"),
          Sender("Jay Gatsby", "199288333", "US", "New York", "Unknown", None, "jay@gatsby.com"),
          "green light"
        )

        t.isDefined === true
        val uuid = t.get.id
        val headers = FakeHeaders(Seq("ACCEPT" -> Seq("application/json")))

        val url = "/transactions/" + uuid.toString
        var page = route(FakeRequest(GET, url)).get
        status(page) must equalTo(OK)
        contentType(page) must beSome.which(_ == "text/html")

        page = route(FakeRequest(GET, url, headers, "")).get
        status(page) must equalTo(OK)
        contentType(page) must beSome.which(_ == "application/json")
      }
    }

    "fetches a entry by code and secret" in {
      running(FakeApplication()) {
        Transaction.create(
          400000,
          Receiver("Rachel Salas", "2349999", "US"),
          Sender("Will Salas", "686278912", "US", "Dayton", "Unknown", None, "will.s@ghetto.com"),
          "LoveConquersTime"
        ).isDefined === true

        val t = Transaction.create(
          2920,
          Receiver("Will Salas", "686278912", "US"),
          Sender("Sylvia Weis", "9297392382", "US", "Greenwich", "Unknown", None, "golden.gurl@mailbox.com"),
          "TimeWillTell"
        )

        t.isDefined === true
        val code = t.get.transactionCode

        
        var page = route(FakeRequest(
          GET, "/transactions?code=" + code + "&secret=TimeWillTell"
        )).get

        status(page) must equalTo(OK)
        contentAsString(page) must contain("Will Salas")
        contentAsString(page) must not contain("Rachel Salas")
      }
    }

    "create a new resource" in {
      running(FakeApplication()) {
        val count = Transaction.count

        val url = "/transactions"
        val body = "amount=4000&payment=1&secret=Winer92&receiver_name=Q&receiver_mobile=123&receiver_country=XX&sender_name=Z&sender_address=Nothing+32&sender_phonenumber=5550&sender_email=blank%40example.com&sender_city=NYC&sender_country=US"
        val params = Map(
          "amount"             -> "4000",
          "payment"            -> "1",
          "secret"             -> "Winer92",
          "receiver_name"      -> "Q",
          "receiver_mobile"    -> "1234",
          "receiver_country"   -> "XX",
          "sender_name"        -> "Z",
          "sender_address"     -> "Nothing 32",
          "sender_phonenumber" -> "5550",
          "sender_email"       -> "blank@example.com",
          "sender_city"        -> "NYC",
          "sender_country"     -> "US"
        )
        val form = Form(
          mapping(
            "amount"           -> number,
            "payment"          -> number,
            "secret"           -> nonEmptyText,
            "sender_name"      -> nonEmptyText,
            "sender_address"   -> nonEmptyText,
            "sender_city"      -> nonEmptyText,
            "sender_country"   -> nonEmptyText,
            "receiver_name"    -> nonEmptyText, 
            "receiver_mobile"  -> nonEmptyText,
            "receiver_country" -> nonEmptyText
          )(TransactionForm.apply)(TransactionForm.unapply)
        )

        form.bind(params).hasErrors must beFalse
        form.errors.size must equalTo(0)

        val page = route(FakeRequest(POST, url)
          .withFormUrlEncodedBody(params.toList: _*)
        ).get

        status(page) must equalTo(CREATED)
        Transaction.count === count+1
      }
    }

    "withdraws the entry" in {
      running(FakeApplication()) {
        var t = Transaction.create(
          40000,
          Receiver("Daisy Buchanan", "686278912", "US"),
          Sender("Jay Gatsby", "199288333", "US", "New York", "Unknown", None, "jay@gatsby.com"),
          "green light"
        )

        t.isDefined === true
        val url = "/transactions/" + t.get.id.toString
        val code = t.get.transactionCode
        status(route(FakeRequest(PUT, url)).get) must equalTo(CONFLICT)
        Transaction.findById(t.get.id).get.withdrawal.isDefined === false

        status(route(FakeRequest(
          PUT, url + "?secret=" + "green+light"
        )).get) must equalTo(CONFLICT)
        Transaction.findById(t.get.id).get.withdrawal.isDefined === false

        status(route(FakeRequest(
          PUT, url + "?code=" + code
        )).get) must equalTo(CONFLICT)
        Transaction.findById(t.get.id).get.withdrawal.isDefined === false

        status(route(FakeRequest(
          PUT, url + "?code=121233211" + "&secret=" + "green+light"
        )).get) must equalTo(CONFLICT)
        Transaction.findById(t.get.id).get.withdrawal.isDefined === false

        status(route(FakeRequest(
          PUT, url + "?code=" + code + "&secret=" + "joker"
        )).get) must equalTo(CONFLICT)
        Transaction.findById(t.get.id).get.withdrawal.isDefined === false

        status(route(FakeRequest(
          PUT, url + "?code=" + code + "&secret=" + "green+light"
        )).get) must equalTo(OK)
        Transaction.findById(t.get.id).get.withdrawal.isDefined === true

        val old_withdrawal = Transaction.findById(t.get.id).get.withdrawal
        status(route(FakeRequest(
          PUT, url + "?code=" + code + "&secret=" + "green+light"
        )).get) must equalTo(CONFLICT)
        Transaction.findById(t.get.id).get.withdrawal.isDefined === true
        Transaction.findById(t.get.id).get.withdrawal must equalTo(old_withdrawal)
      }
    }

  }
}