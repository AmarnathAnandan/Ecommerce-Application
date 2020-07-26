package com.consleague.assessment.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.consleague.assessment.dao.MaterialDAO;
import com.consleague.assessment.dao.OrderDAO;
import com.consleague.assessment.dao.ProductDAO;
import com.consleague.assessment.entity.Product;
import com.consleague.assessment.form.CustomerForm;
import com.consleague.assessment.model.CartInfo;
import com.consleague.assessment.model.CartLineInfo;
import com.consleague.assessment.model.CustomerInfo;
import com.consleague.assessment.model.ProductInfo;
import com.consleague.assessment.pagination.PaginationResult;
import com.consleague.assessment.utils.Utils;
import com.consleague.assessment.validator.CustomerFormValidator;

@Controller
@Transactional
public class MainController {

	@Autowired
	private CustomerFormValidator customerFormValidator;

	@Autowired
	private OrderDAO orderDAO;

	@Autowired
	private ProductDAO productDAO;

	@Autowired
	private MaterialDAO materialDAO;

	@RequestMapping("/403")
	public String accessDenied() {
		return "/403";
	}

	@RequestMapping("/")
	public String home() {
		return "index";
	}

	@RequestMapping({ "/buyProduct" })
	public String listProductHandler(HttpServletRequest request, Model model, //
			@RequestParam(value = "code", defaultValue = "") String code) {

		Product product = null;
		if (code != null && code.length() > 0)
			product = productDAO.findProduct(code);
		if (product != null) {

			//
			CartInfo cartInfo = Utils.getCartInSession(request);

			ProductInfo productInfo = new ProductInfo(product);

			cartInfo.addProduct(productInfo, 1);
		}

		return "redirect:/shoppingCart";
	}

	// Product List
	@RequestMapping({ "/productList" })
	public String listProductHandler(Model model, //
			@RequestParam(value = "name", defaultValue = "") String likeName,
			@RequestParam(value = "page", defaultValue = "1") int page) {
		final int maxResult = 5;
		final int maxNavigationPage = 10;

		PaginationResult<ProductInfo> result = productDAO.queryProducts(page, //
				maxResult, maxNavigationPage, likeName);

		model.addAttribute("paginationProducts", result);
		return "productList";
	}

	@InitBinder
	public void myInitBinder(WebDataBinder dataBinder) {
		Object target = dataBinder.getTarget();
		if (target == null)
			return;
		System.out.println("Target=" + target);

		// Case update quantity in cart
		// (@ModelAttribute("cartForm") @Validated CartInfo cartForm)
		if (target.getClass() == CartInfo.class) {

		}

		// Case save customer information.
		// (@ModelAttribute @Validated CustomerInfo customerForm)
		else if (target.getClass() == CustomerForm.class)
			dataBinder.setValidator(customerFormValidator);

	}

	@RequestMapping(value = { "/productImage" }, method = RequestMethod.GET)
	public void productImage(HttpServletRequest request, HttpServletResponse response, Model model,
			@RequestParam("code") String code) throws IOException {
		Product product = null;
		if (code != null)
			product = this.productDAO.findProduct(code);
		if (product != null && product.getImage() != null) {
			response.setContentType("image/jpeg, image/jpg, image/png, image/gif");
			response.getOutputStream().write(product.getImage());
		}
		response.getOutputStream().close();
	}

	@RequestMapping({ "/shoppingCartRemoveProduct" })
	public String removeProductHandler(HttpServletRequest request, Model model, //
			@RequestParam(value = "code", defaultValue = "") String code) {
		Product product = null;
		if (code != null && code.length() > 0)
			product = productDAO.findProduct(code);
		if (product != null) {

			CartInfo cartInfo = Utils.getCartInSession(request);

			ProductInfo productInfo = new ProductInfo(product);

			cartInfo.removeProduct(productInfo);

		}

		return "redirect:/shoppingCart";
	}

	// GET: Show information to confirm.
	@RequestMapping(value = { "/shoppingCartConfirmation" }, method = RequestMethod.GET)
	public String shoppingCartConfirmationReview(HttpServletRequest request, Model model) {
		CartInfo cartInfo = Utils.getCartInSession(request);

		if (cartInfo == null || cartInfo.isEmpty())
			return "redirect:/shoppingCart";
		else if (!cartInfo.isValidCustomer())
			return "redirect:/shoppingCartCustomer";
		model.addAttribute("myCart", cartInfo);

		return "shoppingCartConfirmation";
	}

	private void deductRawMaterialsCount(CartInfo cartInfo) {
		Map<String, Integer> materialMap = new HashMap<String, Integer>();
		List<CartLineInfo> cartLines = cartInfo.getCartLines();

		for (CartLineInfo s : cartLines) {
			materialMap.put(s.getProductInfo().getCode(), s.getQuantity());
		}

		materialDAO.doDeduction(materialMap);
		System.out.println(materialMap);

	}

	// POST: Submit Cart (Save)
	@RequestMapping(value = { "/shoppingCartConfirmation" }, method = RequestMethod.POST)

	public String shoppingCartConfirmationSave(HttpServletRequest request, Model model) {
		CartInfo cartInfo = Utils.getCartInSession(request);

		if (cartInfo.isEmpty())
			return "redirect:/shoppingCart";
		else if (!cartInfo.isValidCustomer())
			return "redirect:/shoppingCartCustomer";
		try {
			deductRawMaterialsCount(cartInfo);

			orderDAO.saveOrder(cartInfo);
		} catch (Exception e) {

			return "shoppingCartConfirmation";
		}

		// Remove Cart from Session.
		Utils.removeCartInSession(request);

		// Store last cart.
		Utils.storeLastOrderedCartInSession(request, cartInfo);

		return "redirect:/shoppingCartFinalize";
	}

	// GET: Enter customer information.
	@RequestMapping(value = { "/shoppingCartCustomer" }, method = RequestMethod.GET)
	public String shoppingCartCustomerForm(HttpServletRequest request, Model model) {

		CartInfo cartInfo = Utils.getCartInSession(request);

		if (cartInfo.isEmpty())
			return "redirect:/shoppingCart";
		CustomerInfo customerInfo = cartInfo.getCustomerInfo();

		CustomerForm customerForm = new CustomerForm(customerInfo);

		model.addAttribute("customerForm", customerForm);

		return "shoppingCartCustomer";
	}

	// POST: Save customer information.
	@RequestMapping(value = { "/shoppingCartCustomer" }, method = RequestMethod.POST)
	public String shoppingCartCustomerSave(HttpServletRequest request, //
			Model model, //
			@ModelAttribute("customerForm") @Validated CustomerForm customerForm, //
			BindingResult result, //
			final RedirectAttributes redirectAttributes) {

		if (result.hasErrors()) {
			customerForm.setValid(false);
			// Forward to reenter customer info.
			return "shoppingCartCustomer";
		}

		customerForm.setValid(true);
		CartInfo cartInfo = Utils.getCartInSession(request);
		CustomerInfo customerInfo = new CustomerInfo(customerForm);
		cartInfo.setCustomerInfo(customerInfo);

		return "redirect:/shoppingCartConfirmation";
	}

	@RequestMapping(value = { "/shoppingCartFinalize" }, method = RequestMethod.GET)
	public String shoppingCartFinalize(HttpServletRequest request, Model model) {

		CartInfo lastOrderedCart = Utils.getLastOrderedCartInSession(request);

		if (lastOrderedCart == null)
			return "redirect:/shoppingCart";
		model.addAttribute("lastOrderedCart", lastOrderedCart);
		return "shoppingCartFinalize";
	}

	// GET: Show cart.
	@RequestMapping(value = { "/shoppingCart" }, method = RequestMethod.GET)
	public String shoppingCartHandler(HttpServletRequest request, Model model) {
		CartInfo myCart = Utils.getCartInSession(request);

		model.addAttribute("cartForm", myCart);
		return "shoppingCart";
	}

	// POST: Update quantity for product in cart
	@RequestMapping(value = { "/shoppingCart" }, method = RequestMethod.POST)
	public String shoppingCartUpdateQty(HttpServletRequest request, //
			Model model, //
			@ModelAttribute("cartForm") CartInfo cartForm) {

		CartInfo cartInfo = Utils.getCartInSession(request);
		cartInfo.updateQuantity(cartForm);

		return "redirect:/shoppingCart";
	}

}