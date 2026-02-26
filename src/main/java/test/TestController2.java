package test;

import servlet.annotations.Controller;
import servlet.annotations.Url;

@Controller
public class TestController2 {

    @Url("/test2")
    public String test() {
        return "Resultat du TestController2";
    }
}
