package test;

import servlet.annotations.Controller;
import servlet.annotations.Url;

@Controller
public class TestController3 {

    @Url("/test3")
    public String test() {
        return "Resultat du TestController3";
    }
}
