const puppeteer = require('puppeteer');
const cas = require('../../cas.js');

(async () => {
    console.log("Refreshing application context...")
    let response = await cas.doRequest("https://localhost:8443/cas/actuator/refresh", "POST");
    console.log(response)
    
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);
    await cas.goto(page, "https://localhost:8443/cas/login?authn_method=mfa-u2f");
    await cas.loginWith(page, "casuser", "Mellon");
    await cas.assertTextContent(page, "#login h3", "Register Device")
    await cas.assertTextContent(page, "#login p", "Please touch the flashing U2F device now.")
    await browser.close();
})();
