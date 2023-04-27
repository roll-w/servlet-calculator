/*
 * Copyright (C) 2022 RollW
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.rollw.calculator.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tech.rollw.calculator.core.CalculatorEvaluator;
import tech.rollw.calculator.core.CalculatorException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author RollW
 */
@WebServlet(name = "ExpressionServlet", value = "/api/eval")
public class ExpressionServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setHeader(resp);
        String expression = req.getParameter("expression");

        if (expression == null || expression.isEmpty()) {
            MessagePack pack = new MessagePack(false, "Expression cannot be empty");
            writeJson(resp, pack);
            return;
        }

        CalculatorEvaluator evaluator = new CalculatorEvaluator(expression);
        try {
            CalculatorEvaluator.Result result = evaluator.evaluate();
            writeJson(resp, result);
        } catch (CalculatorException e) {
            MessagePack pack = new MessagePack(false, e.getMessage());
            writeJson(resp, pack);
        }
    }

    protected void setHeader(HttpServletResponse response) {
        response.setCharacterEncoding("utf8");
        response.setContentType("application/json;charset=utf-8");
    }

    protected void writeJson(HttpServletResponse resp, Object obj) throws IOException {
        try {
            PrintWriter writer = resp.getWriter();
            Gson gson = new GsonBuilder()
                    .create();
            writer.print(gson.toJson(obj));
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }

    }
}
