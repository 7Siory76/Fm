package test;

import servlet.annotations.Controller;
import servlet.annotations.Url;

@Controller
public class TestController1 {

    @Url("/test1")
    public String test() {
        return "Resultat du TestController1";
    }
}
